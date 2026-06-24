"use client";

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { api } from '../../lib/api';
import { useAuth } from '../../lib/auth';

interface CartItem {
  product_id: string;
  quantity: number;
  price: number;
  name?: string;
  image_url?: string;
}

interface OrderResult {
  order_id: string;
  status: string;
  total_amount: number;
}

export default function CheckoutPage() {
  const router = useRouter();
  const { user, getAuthHeaders } = useAuth();
  const userId = user?.id || 'user123';
  const [cartItems, setCartItems] = useState<CartItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [placing, setPlacing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [orderResult, setOrderResult] = useState<OrderResult | null>(null);
  const [address, setAddress] = useState({
    street: '',
    city: '',
    state: '',
    zip: '',
    country: 'US',
  });

  useEffect(() => {
    async function loadCart() {
      try {
        const res = await fetch(`${api.cart(userId)}/items`, {
          headers: getAuthHeaders(),
        });
        if (!res.ok) throw new Error('Failed to load cart');
        setCartItems(await res.json());
      } catch (err) {
        setError(err instanceof Error ? err.message : 'Unable to load cart');
      } finally {
        setLoading(false);
      }
    }
    loadCart();
  }, [userId, getAuthHeaders]);

  const total = cartItems.reduce((sum, item) => sum + item.price * item.quantity, 0);

  async function placeOrder() {
    setPlacing(true);
    setError(null);
    try {
      const headers: Record<string, string> = {
        'Content-Type': 'application/json',
        'x-trace-id': crypto.randomUUID(),
        ...getAuthHeaders(),
      };
      const body: Record<string, unknown> = {
        user_id: userId,
        total_amount: total,
        items: cartItems.map((item) => ({
          product_id: item.product_id,
          quantity: item.quantity,
          unit_price: item.price,
        })),
        shipping_address: {
          street: address.street,
          city: address.city,
          state: address.state,
          zip: address.zip,
          country: address.country,
        },
      };
      if (address.street) {
        body.shipping_address = {
          street: address.street,
          city: address.city,
          state: address.state,
          zip: address.zip,
          country: address.country,
        };
      }

      const res = await fetch(api.orders, {
        method: 'POST',
        headers,
        body: JSON.stringify(body),
      });
      if (!res.ok) throw new Error(`Order failed: ${res.status}`);
      setOrderResult(await res.json());
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unable to place order');
    } finally {
      setPlacing(false);
    }
  }

  if (orderResult) {
    return (
      <div className="site-container">
        <div className="page-panel text-center">
          <h1 className="page-title">Order Placed!</h1>
          <div className="max-w-md mx-auto space-y-4">
            <div className="page-card">
              <p className="text-lg font-semibold">Order ID</p>
              <p className="text-sm text-gray-600 break-all">{orderResult.order_id}</p>
              <p className="mt-2">
                Status: <span className="font-semibold text-blue-600">{orderResult.status}</span>
              </p>
              <p className="mt-2 text-lg font-semibold">Total: ${orderResult.total_amount.toFixed(2)}</p>
            </div>
            <p className="text-sm text-gray-600">
              Your order is being processed. The saga orchestrator will coordinate payment and shipping.
              Check your orders page for status updates.
            </p>
            <button onClick={() => router.push('/orders')} className="btn">View Orders</button>
            <button onClick={() => router.push('/catalog')} className="btn-secondary ml-2">Continue Shopping</button>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="site-container">
      <div className="page-panel">
        <h1 className="page-title">Checkout</h1>
        <p className="page-subtitle">Review your order and place it.</p>

        {loading && <p>Loading cart...</p>}
        {error && <p className="text-red-600">{error}</p>}

        {!loading && cartItems.length === 0 && !orderResult && (
          <div className="page-card text-center">
            <p>Your cart is empty.</p>
            <button onClick={() => router.push('/catalog')} className="btn mt-4">Browse Catalog</button>
          </div>
        )}

        {!loading && cartItems.length > 0 && (
          <div className="grid gap-8 xl:grid-cols-[1.7fr_0.8fr]">
            <div className="space-y-4">
              <h2 className="text-xl font-semibold">Items ({cartItems.length})</h2>
              {cartItems.map((item) => (
                <div key={item.product_id} className="page-card flex justify-between items-center">
                  <div>
                    <p className="font-medium">{item.name || `Product ${item.product_id}`}</p>
                    <p className="text-sm text-gray-600">Qty: {item.quantity} × ${item.price.toFixed(2)}</p>
                  </div>
                  <p className="font-semibold">${(item.quantity * item.price).toFixed(2)}</p>
                </div>
              ))}

              <h2 className="text-xl font-semibold mt-8">Shipping Address</h2>
              <div className="page-card space-y-3">
                <input className="w-full p-2 border rounded" value={address.street} onChange={(e) => setAddress({ ...address, street: e.target.value })} placeholder="Street address" />
                <div className="flex gap-3">
                  <input className="flex-1 p-2 border rounded" value={address.city} onChange={(e) => setAddress({ ...address, city: e.target.value })} placeholder="City" />
                  <input className="w-20 p-2 border rounded" value={address.state} onChange={(e) => setAddress({ ...address, state: e.target.value })} placeholder="State" />
                  <input className="w-28 p-2 border rounded" value={address.zip} onChange={(e) => setAddress({ ...address, zip: e.target.value })} placeholder="ZIP" />
                </div>
              </div>
            </div>

            <div className="page-summary">
              <h2 className="text-xl font-semibold mb-4">Order Summary</h2>
              <div className="space-y-3">
                <div className="flex justify-between text-sm text-gray-600">
                  <span>Subtotal</span>
                  <span>${total.toFixed(2)}</span>
                </div>
                <div className="flex justify-between text-sm text-gray-600">
                  <span>Shipping</span>
                  <span>$9.99</span>
                </div>
                <div className="flex justify-between text-base font-semibold">
                  <span>Total</span>
                  <span>${(total + 9.99).toFixed(2)}</span>
                </div>
              </div>
              <button
                onClick={placeOrder}
                disabled={placing}
                className="btn mt-6 w-full text-center"
              >
                {placing ? 'Placing Order...' : 'Place Order'}
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
