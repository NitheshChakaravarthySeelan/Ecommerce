"""
Payment Service (Python/FastAPI + PostgreSQL + Kafka)

Processes payments for orders. Supports two modes:

1. REST (synchronous) — POST /payments — for direct, non-saga scenarios
2. Event-driven (async) — consumes `payment-initiated` from Kafka,
   processes the payment, and publishes `payment-processed`

Saga data flow:
  orchestrator ──→ payment-initiated topic ──→ Payment Service
                                                    │
                          ┌─────────────────────────┤
                          │                         │
                     amount > 0               amount ≤ 0
                     (SUCCEEDED)               (FAILED)
                          │                         │
                          └──────┬──────────────────┘
                                 ▼
                    payment-processed topic
                                 │
                                 ▼
                          orchestrator continues
"""

import json
import logging
import os
import time
from contextlib import asynccontextmanager
from uuid import uuid4

import asyncio
from aiokafka import AIOKafkaConsumer, AIOKafkaProducer
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from sqlalchemy import Column, Float, Integer, String, create_engine
from sqlalchemy.exc import OperationalError
from sqlalchemy.orm import declarative_base, sessionmaker

logger = logging.getLogger("payment-service")
logger.setLevel(logging.INFO)
handler = logging.StreamHandler()
handler.setFormatter(logging.Formatter(
    '{"timestamp":"%(asctime)s","name":"%(name)s","level":"%(levelname)s","traceId":"%(traceId)s","message":"%(message)s"}',
    datefmt="%Y-%m-%dT%H:%M:%SZ"
))
logger.addHandler(handler)
logger.propagate = False


class TraceFilter(logging.Filter):
    """Injects an empty traceId into log records that lack one."""
    def filter(self, record):
        if not hasattr(record, 'traceId'):
            record.traceId = ''
        return True


logger.addFilter(TraceFilter())

DATABASE_URL = os.getenv(
    "DATABASE_URL",
    "postgresql+psycopg://ecommerce:changeme@postgres:5432/ecommerce"
)
KAFKA_BOOTSTRAP_SERVERS = os.getenv("KAFKA_BOOTSTRAP_SERVERS", "kafka:9092")

engine = create_engine(DATABASE_URL, future=True)
SessionLocal = sessionmaker(bind=engine, future=True)
Base = declarative_base()

producer = None


# ── Pydantic models (API layer) ────────────────────────────────────

class PaymentRequest(BaseModel):
    """Incoming payment request via REST endpoint."""
    order_id: str
    amount: float
    method: str


class PaymentResponse(BaseModel):
    """Payment result returned to the caller."""
    payment_id: str
    order_id: str
    status: str


# ── SQLAlchemy model (DB layer) ────────────────────────────────────

class PaymentDB(Base):
    """Persistent payment record in the `payments` table."""
    __tablename__ = "payments"
    id = Column(Integer, primary_key=True, index=True)
    payment_id = Column(String, unique=True, nullable=False, index=True)
    order_id = Column(String, nullable=False)
    amount = Column(Float, nullable=False)
    method = Column(String, nullable=False)
    status = Column(String, nullable=False)


# ── Kafka helpers ──────────────────────────────────────────────────

async def publish_payment_processed(payment_id: str, order_id: str, amount: float, status: str, trace_id: str):
    """
    Publish a `payment-processed` event to Kafka so the orchestrator
    can proceed to the next saga step (shipping initiation).
    """
    event = {
        "paymentId": payment_id,
        "orderId": order_id,
        "amount": amount,
        "status": status,
        "traceId": trace_id,
        "timestamp": int(time.time() * 1000),
    }
    await producer.send("payment-processed", key=order_id.encode(), value=json.dumps(event).encode())
    logger.info("Published payment-processed for order %s status=%s", order_id, status, extra={"traceId": trace_id})


async def handle_payment_initiated():
    """
    Background task: consume `payment-initiated` events from Kafka.

    For each event:
      1. Determine status: SUCCEEDED if amount > 0, FAILED otherwise
      2. Save the payment record to PostgreSQL
      3. Publish `payment-processed` back to Kafka

    This is a mock implementation — no real payment gateway is called.
    """
    consumer = AIOKafkaConsumer(
        "payment-initiated",
        bootstrap_servers=KAFKA_BOOTSTRAP_SERVERS,
        group_id="payment-group",
        value_deserializer=lambda m: json.loads(m.decode()),
    )
    await consumer.start()
    try:
        async for msg in consumer:
            data = msg.value
            order_id = data.get("orderId")
            amount = data.get("amount", 0)
            trace_id = data.get("traceId", "")
            logger.info("Received payment-initiated for order %s", order_id, extra={"traceId": trace_id})

            # Mock payment logic — real integration would call a payment gateway here
            status = "SUCCEEDED" if amount > 0 else "FAILED"
            payment_id = str(uuid4())
            payment_db = PaymentDB(
                payment_id=payment_id,
                order_id=order_id,
                amount=amount,
                method="card",
                status=status,
            )
            with SessionLocal() as session:
                session.add(payment_db)
                session.commit()

            await publish_payment_processed(payment_id, order_id, amount, status, trace_id)
    finally:
        await consumer.stop()


# ── Application lifecycle ──────────────────────────────────────────

@asynccontextmanager
async def lifespan(app: FastAPI):
    """Startup: create Kafka producer and launch the payment-initiated consumer."""
    global producer
    producer = AIOKafkaProducer(bootstrap_servers=KAFKA_BOOTSTRAP_SERVERS)
    await producer.start()
    asyncio.create_task(handle_payment_initiated())
    logger.info("Payment service started")
    yield
    await producer.stop()

app = FastAPI(lifespan=lifespan)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.on_event("startup")
def startup_event():
    """
    Create database tables on startup with retry logic.

    Retries up to 10 times with 2-second delays to handle the race
    condition where PostgreSQL is not yet accepting connections.
    """
    for _ in range(10):
        try:
            Base.metadata.create_all(bind=engine)
            logger.info("Database tables created")
            return
        except OperationalError:
            time.sleep(2)
    raise RuntimeError("Could not connect to the database during startup")


# ── REST endpoints ─────────────────────────────────────────────────

@app.get('/health')
def health():
    return {"status": "ok"}


@app.get('/payments/{payment_id}', response_model=PaymentResponse)
def get_payment(payment_id: str):
    """Return a payment record by its UUID. Returns 404 if not found."""
    with SessionLocal() as session:
        payment_db = session.query(PaymentDB).filter_by(payment_id=payment_id).first()
        if not payment_db:
            raise HTTPException(status_code=404, detail='Payment not found')
        return PaymentResponse(
            payment_id=payment_db.payment_id,
            order_id=payment_db.order_id,
            status=payment_db.status,
        )


@app.post('/payments', response_model=PaymentResponse)
def process_payment(request: PaymentRequest):
    """
    Synchronous REST endpoint for direct payment processing.

    This is a fallback path (not used by the saga). The saga uses
    the Kafka event-driven path instead.

    Mock logic: payments succeed if amount > 0, fail otherwise.
    """
    status = 'SUCCEEDED' if request.amount > 0 else 'FAILED'
    payment_id = str(uuid4())
    payment_db = PaymentDB(
        payment_id=payment_id,
        order_id=request.order_id,
        amount=request.amount,
        method=request.method,
        status=status,
    )
    with SessionLocal() as session:
        session.add(payment_db)
        session.commit()
    return PaymentResponse(payment_id=payment_id, order_id=request.order_id, status=status)
