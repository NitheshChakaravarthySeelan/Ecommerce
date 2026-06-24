import pytest
from fastapi.testclient import TestClient
from unittest.mock import patch, AsyncMock
import sys
import os
sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..'))

# Must patch before importing app
with patch('main.AIOKafkaProducer') as mock_producer_class, \
     patch('main.AIOKafkaConsumer') as mock_consumer_class:
    mock_producer_instance = AsyncMock()
    mock_producer_class.return_value = mock_producer_instance
    mock_consumer_instance = AsyncMock()
    mock_consumer_class.return_value = mock_consumer_instance

    from main import app

client = TestClient(app)

@pytest.fixture(autouse=True)
def setup_db():
    """Use in-memory SQLite for testing"""
    import main
    from sqlalchemy import create_engine
    main.DATABASE_URL = "sqlite:///./test_orders.db"
    main.engine = create_engine(main.DATABASE_URL, connect_args={"check_same_thread": False})
    main.SessionLocal = __import__('sqlalchemy').orm.sessionmaker(bind=main.engine)
    main.Base.metadata.create_all(bind=main.engine)
    yield
    main.Base.metadata.drop_all(bind=main.engine)
    if os.path.exists("./test_orders.db"):
        os.remove("./test_orders.db")

def test_health():
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json() == {"status": "ok"}

def test_create_order():
    response = client.post("/orders", json={
        "user_id": "user-1",
        "total_amount": 100.0,
        "items": [{"product_id": "p1", "quantity": 2, "unit_price": 50.0}]
    })
    assert response.status_code == 200
    data = response.json()
    assert data["user_id"] == "user-1"
    assert data["status"] == "PENDING"
    assert data["total_amount"] == 100.0
    assert len(data["items"]) == 1
    assert data["items"][0]["product_id"] == "p1"

def test_get_order():
    create_resp = client.post("/orders", json={
        "user_id": "user-1",
        "total_amount": 50.0,
        "items": [{"product_id": "p2", "quantity": 1, "unit_price": 50.0}]
    })
    order_id = create_resp.json()["order_id"]

    response = client.get(f"/orders/{order_id}")
    assert response.status_code == 200
    assert response.json()["order_id"] == order_id

def test_get_order_not_found():
    response = client.get("/orders/nonexistent")
    assert response.status_code == 404

def test_list_orders():
    client.post("/orders", json={
        "user_id": "user-1",
        "total_amount": 30.0,
        "items": [{"product_id": "p3", "quantity": 3, "unit_price": 10.0}]
    })
    client.post("/orders", json={
        "user_id": "user-2",
        "total_amount": 20.0,
        "items": [{"product_id": "p4", "quantity": 1, "unit_price": 20.0}]
    })

    response = client.get("/orders")
    assert response.status_code == 200
    assert len(response.json()) >= 2
