"""
Cart Service (Python/FastAPI + Redis)

Manages shopping cart state per user using Redis as a fast in-memory store.
All cart data is ephemeral — no PostgreSQL dependency.

Endpoints:
  GET    /cart/{user_id}           — return the full cart object
  POST   /cart/{user_id}/items     — add or update an item in the cart
  GET    /cart/{user_id}/items     — list items in the cart
  PATCH  /cart/{user_id}/items/{item_id} — update quantity/price of an item
  DELETE /cart/{user_id}/items/{item_id} — remove an item from the cart

Data flow:
  Each cart is stored as a JSON string under the Redis key "cart:{user_id}".
  Load → modify → save (read-after-write). No atomicity guarantee beyond
  Redis's single-threaded command execution.
"""

import json
import logging
import os
from typing import List, Optional
from uuid import uuid4

import redis.asyncio as redis
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel

logger = logging.getLogger("cart-service")
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

app = FastAPI()

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

REDIS_URL = os.getenv("REDIS_URL", "redis://redis:6379/0")
redis_client = redis.from_url(REDIS_URL, decode_responses=True)


class CartItem(BaseModel):
    """A single product entry inside a shopping cart."""
    item_id: Optional[str] = None
    product_id: str
    quantity: int
    price: float


class Cart(BaseModel):
    """A user's shopping cart containing zero or more items."""
    cart_id: str
    user_id: str
    items: List[CartItem] = []


async def load_cart(user_id: str) -> Cart:
    """Deserialize the user's cart from Redis, or return an empty cart."""
    raw = await redis_client.get(f"cart:{user_id}")
    if not raw:
        return Cart(cart_id=str(uuid4()), user_id=user_id, items=[])
    return Cart.model_validate(json.loads(raw))


async def save_cart(cart: Cart) -> Cart:
    """Serialize and persist the cart to Redis."""
    await redis_client.set(f"cart:{cart.user_id}", cart.model_dump_json())
    return cart


@app.get('/cart/{user_id}', response_model=Cart)
async def get_cart(user_id: str):
    """Return the full cart for a given user, including all items."""
    logger.info("Fetching cart for user %s", user_id)
    return await load_cart(user_id)


@app.post('/cart/{user_id}/items', response_model=Cart)
async def add_item(user_id: str, item: CartItem):
    """
    Add a product to the cart or update its quantity if already present.
    If the product already exists in the cart, the quantity is incremented
    and the price is overwritten with the latest value.
    """
    logger.info("Adding item %s to cart for user %s", item.product_id, user_id)
    cart = await load_cart(user_id)
    existing = next((entry for entry in cart.items if entry.product_id == item.product_id), None)
    if existing:
        existing.quantity += item.quantity
        existing.price = item.price
    else:
        item.item_id = item.item_id or str(uuid4())
        cart.items.append(item)
    return await save_cart(cart)


@app.get('/cart/{user_id}/items', response_model=List[CartItem])
async def get_items(user_id: str):
    """Return only the item list from the user's cart."""
    cart = await load_cart(user_id)
    return cart.items


@app.get('/health')
async def health():
    """Health check — does NOT verify Redis connectivity."""
    return {"status": "ok"}


@app.patch('/cart/{user_id}/items/{item_id}', response_model=Cart)
async def update_item(user_id: str, item_id: str, item: CartItem):
    """Update the quantity and price of a specific item in the cart."""
    logger.info("Updating item %s for user %s", item_id, user_id)
    cart = await load_cart(user_id)
    for existing in cart.items:
        if existing.item_id == item_id:
            existing.quantity = item.quantity
            existing.price = item.price
            return await save_cart(cart)
    raise HTTPException(status_code=404, detail='Item not found')


@app.delete('/cart/{user_id}/items/{item_id}', response_model=Cart)
async def delete_item(user_id: str, item_id: str):
    """Remove an item from the cart by its item_id."""
    logger.info("Removing item %s from cart for user %s", item_id, user_id)
    cart = await load_cart(user_id)
    cart.items = [entry for entry in cart.items if entry.item_id != item_id]
    return await save_cart(cart)


@app.on_event("shutdown")
async def shutdown():
    """Close the Redis connection on graceful shutdown."""
    await redis_client.close()
