"use client";

import { useEffect, useState } from 'react';
import { api } from '../../lib/api';

interface OrderItem {
  product_id: string;
  quantity: number;
  unit_price: number;
}

interface Order {
  order_id: string;
  user_id: string;
  status: string;
  total_amount: number;
  items: OrderItem[];
}

export default function OrdersPage() {
  const [orders, setOrders] = useState<Order[]>([]);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    loadOrders();
  }, []);

  async function loadOrders() {
    setError(null);
    try {
      const response = await fetch(api.orders);
      if (!response.ok) {
        throw new Error(`Order list failed: ${response.status}`);
      }
      setOrders(await response.json());
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unable to fetch orders');
    }
  }

  async function createSampleOrder() {
    setMessage(null);
    setError(null);
    try {
      const response = await fetch(api.orders, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          order_id: 'sample-order',
          user_id: 'user123',
          status: 'PENDING',
          total_amount: 129.99,
          items: [{ product_id: 'p1', quantity: 1, unit_price: 129.99 }],
        }),
      });
      if (!response.ok) {
        throw new Error(`Create order failed: ${response.status}`);
      }
      const order = await response.json();
      setMessage(`Created order ${order.order_id}`);
      await loadOrders();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unable to create order');
    }
  }

  return (
    <div className="content">
      <h1 className="text-2xl font-semibold">Order Service</h1>
      <p className="text-gray-600">List orders and create a sample order.</p>
      {error && <p className="text-red-600">{error}</p>}
      {message && <p className="text-green-600">{message}</p>}
      <div className="mt-4">
        <button onClick={createSampleOrder} className="btn">Create Sample Order</button>
        <button onClick={loadOrders} className="px-4 py-2 border rounded ml-3">Refresh Orders</button>
      </div>
      <div className="mt-6">
        {orders.length ? (
          orders.map((order) => (
            <div key={order.order_id} className="mb-4 border-b border-gray-200 pb-3">
              <h2 className="font-medium">{order.order_id}</h2>
              <p>User: {order.user_id}</p>
              <p>Status: {order.status}</p>
              <p>Total: ${order.total_amount.toFixed(2)}</p>
              <pre className="mt-2">{JSON.stringify(order.items, null, 2)}</pre>
            </div>
          ))
        ) : (
          <p>No orders yet.</p>
        )}
      </div>
    </div>
  );
}
