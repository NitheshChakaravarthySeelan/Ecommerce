"use client";

import { useState } from 'react';
import { api } from '../../lib/api';

interface PaymentResponse {
  payment_id: string;
  order_id: string;
  status: string;
}

export default function PaymentPage() {
  const [orderId, setOrderId] = useState('sample-order');
  const [amount, setAmount] = useState('129.99');
  const [method, setMethod] = useState('CREDIT_CARD');
  const [result, setResult] = useState<PaymentResponse | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function submitPayment() {
    setError(null);
    setResult(null);
    try {
      const response = await fetch(api.payments, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ order_id: orderId, amount: Number(amount), method }),
      });
      if (!response.ok) {
        throw new Error(`Payment failed: ${response.status}`);
      }
      setResult(await response.json());
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Payment request failed');
    }
  }

  return (
    <div className="content">
      <h1 className="text-2xl font-semibold">Payment Service</h1>
      <p className="text-gray-600">Process a payment through the payment service.</p>
      {error && <p className="text-red-600">{error}</p>}
      {result && <p className="text-green-600">Payment {result.status} for {result.order_id}</p>}
      <div className="mt-4">
        <label className="block">
          Order ID:<br />
          <input value={orderId} onChange={(e) => setOrderId(e.target.value)} className="w-full p-2 border rounded" />
        </label>
        <label className="block mt-3">
          Amount:<br />
          <input value={amount} onChange={(e) => setAmount(e.target.value)} className="w-full p-2 border rounded" />
        </label>
        <label className="block mt-3">
          Method:<br />
          <input value={method} onChange={(e) => setMethod(e.target.value)} className="w-full p-2 border rounded" />
        </label>
        <button onClick={submitPayment} className="btn mt-3">Submit Payment</button>
      </div>
      {result && (
        <pre className="mt-4">{JSON.stringify(result, null, 2)}</pre>
      )}
    </div>
  );
}
