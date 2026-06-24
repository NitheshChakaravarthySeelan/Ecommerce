"""
Order Service (Python/FastAPI + PostgreSQL + Kafka)

Manages the lifecycle of customer orders. When an order is created, it is
persisted to PostgreSQL with status PENDING and a `order-created` event is
published to Kafka to trigger the fulfillment saga.

Kafka consumers (background):
  - order-completed → marks order as DELIVERED
  - saga-failed     → marks order as FAILED

Saga data flow:
  POST /orders  ──→ save to DB ──→ publish order-created ──→ orchestrator picks up
                          ↑                                          │
                          └──── order-completed / saga-failed ◄──────┘
"""

import json
import logging
import os
import time
from contextlib import asynccontextmanager
from typing import List
from uuid import uuid4

import asyncio
from aiokafka import AIOKafkaConsumer, AIOKafkaProducer
from fastapi import FastAPI, HTTPException, Request
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from sqlalchemy import Column, Float, Integer, JSON, String, create_engine
from sqlalchemy.exc import OperationalError
from sqlalchemy.orm import declarative_base, sessionmaker

logger = logging.getLogger("order-service")
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

engine = create_engine(
    DATABASE_URL,
    pool_size=int(os.getenv("DB_POOL_SIZE", "5")),
    max_overflow=int(os.getenv("DB_POOL_OVERFLOW", "5")),
    pool_pre_ping=True,
    pool_recycle=300,
    future=True,
)
SessionLocal = sessionmaker(bind=engine, future=True)
Base = declarative_base()

producer = None


def get_trace_id(request: Request) -> str:
    """Extract the trace ID from the incoming request header, or generate one."""
    return request.headers.get("x-trace-id", str(uuid4()))


# ── Pydantic models (API layer) ────────────────────────────────────

class OrderItem(BaseModel):
    """A single product line within an order."""
    product_id: str
    quantity: int
    unit_price: float


class Order(BaseModel):
    """Order response returned to the caller."""
    order_id: str = ""
    user_id: str
    status: str = "PENDING"
    total_amount: float
    items: List[OrderItem]


# ── SQLAlchemy model (DB layer) ────────────────────────────────────

class OrderDB(Base):
    """Persistent order record in the `orders` table."""
    __tablename__ = "orders"
    id = Column(Integer, primary_key=True, index=True)
    order_id = Column(String, unique=True, nullable=False, index=True)
    user_id = Column(String, nullable=False)
    status = Column(String, nullable=False)
    total_amount = Column(Float, nullable=False)
    items = Column(JSON, nullable=False)


# ── Kafka helpers ──────────────────────────────────────────────────

async def publish_order_created(order: Order, trace_id: str):
    """
    Publish an `order-created` event to Kafka.

    The orchestrator consumes this event to begin the fulfillment saga
    (reserve inventory → process payment → arrange shipping).
    If the producer is not available, the event is silently skipped
    and the order remains PENDING — this is a known reliability gap
    (see ARCHITECTURE_ANALYSIS.md: orphan orders).
    """
    if producer is None:
        logger.warning("Kafka producer not available, skipping event", extra={"traceId": trace_id})
        return
    event = {
        "orderId": order.order_id,
        "userId": order.user_id,
        "totalAmount": order.total_amount,
        "traceId": trace_id,
        "timestamp": int(time.time() * 1000),
    }
    await producer.send("order-created", key=order.order_id.encode(), value=json.dumps(event).encode())
    logger.info("Published order-created", extra={"traceId": trace_id})


async def handle_order_completed():
    """
    Background task: consume order-completed and saga-failed events.

    Runs as long as the service is alive. Updates the order status in
    PostgreSQL when the saga finishes (DELIVERED) or fails (FAILED).
    """
    consumer = AIOKafkaConsumer(
        "order-completed", "saga-failed",
        bootstrap_servers=KAFKA_BOOTSTRAP_SERVERS,
        group_id="order-group",
        value_deserializer=lambda m: json.loads(m.decode()),
    )
    await consumer.start()
    try:
        async for msg in consumer:
            data = msg.value
            order_id = data.get("orderId")
            trace_id = data.get("traceId", "")
            logger.info("Received %s for order %s", msg.topic, order_id, extra={"traceId": trace_id})
            with SessionLocal() as session:
                order_db = session.query(OrderDB).filter_by(order_id=order_id).first()
                if order_db:
                    if msg.topic == "order-completed":
                        order_db.status = "DELIVERED"
                    elif msg.topic == "saga-failed":
                        order_db.status = "FAILED"
                    session.commit()
                    logger.info("Order %s status updated to %s", order_id, order_db.status, extra={"traceId": trace_id})
    finally:
        await consumer.stop()


# ── Application lifecycle ──────────────────────────────────────────

@asynccontextmanager
async def lifespan(app: FastAPI):
    """Startup: create Kafka producer and launch the saga-result consumer."""
    global producer
    producer = AIOKafkaProducer(bootstrap_servers=KAFKA_BOOTSTRAP_SERVERS)
    await producer.start()
    asyncio.create_task(handle_order_completed())
    logger.info("Order service started")
    yield
    await producer.stop()

app = FastAPI(lifespan=lifespan)

CORS_ORIGINS = os.getenv("CORS_ALLOWED_ORIGINS", "*").split(",")

app.add_middleware(
    CORSMiddleware,
    allow_origins=CORS_ORIGINS,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.on_event("startup")
def startup_event():
    """
    Create database tables on startup with retry logic.

    Retries up to 10 times with 2-second delays. This gives PostgreSQL
    time to become ready in container orchestration scenarios where
    Docker's depends_on is not sufficient.
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


@app.post('/orders')
async def create_order(order: Order, request: Request):
    """
    Create a new order with status PENDING.

    The order is saved to PostgreSQL first, then a `order-created` event
    is published to Kafka. The orchestrator picks up the event and starts
    the fulfillment saga (inventory → payment → shipping).

    Warning: if Kafka is unavailable, the order is saved but the saga is
    never triggered (orphan order). See ARCHITECTURE_ANALYSIS.md.
    """
    trace_id = get_trace_id(request)
    logger.info("Creating order for user %s", order.user_id, extra={"traceId": trace_id})
    order.order_id = str(uuid4())
    order.status = "PENDING"
    order_db = OrderDB(
        order_id=order.order_id,
        user_id=order.user_id,
        status=order.status,
        total_amount=order.total_amount,
        items=[item.model_dump() for item in order.items],
    )
    with SessionLocal() as session:
        session.add(order_db)
        session.commit()
        session.refresh(order_db)
    await publish_order_created(order, trace_id)
    return order


@app.get('/orders/{order_id}')
def get_order(order_id: str):
    """Return a single order by its UUID. Returns 404 if not found."""
    with SessionLocal() as session:
        order_db = session.query(OrderDB).filter_by(order_id=order_id).first()
        if not order_db:
            raise HTTPException(status_code=404, detail='Order not found')
        return Order(
            order_id=order_db.order_id,
            user_id=order_db.user_id,
            status=order_db.status,
            total_amount=order_db.total_amount,
            items=[OrderItem(**item) for item in order_db.items],
        )


@app.get('/orders', response_model=List[Order])
def list_orders():
    """
    Return all orders.

    Note: no pagination — this will become a performance issue under
    load as the table grows.
    """
    with SessionLocal() as session:
        orders_db = session.query(OrderDB).all()
        return [
            Order(
                order_id=item.order_id,
                user_id=item.user_id,
                status=item.status,
                total_amount=item.total_amount,
                items=[OrderItem(**entry) for entry in item.items],
            )
            for item in orders_db
        ]
