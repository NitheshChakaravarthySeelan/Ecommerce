"use client";

import { useState } from 'react';
import { api } from '../../lib/api';

interface Quote {
  country: string;
  estimatedDeliveryDays: number;
  cost: number;
}

interface Tracking {
  orderId: string;
  status: string;
  estimatedDelivery: string;
}

export default function ShippingPage() {
  const [country, setCountry] = useState('US');
  const [quote, setQuote] = useState<Quote | null>(null);
  const [trackingId, setTrackingId] = useState('sample-order');
  const [tracking, setTracking] = useState<Tracking | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function loadQuote() {
    setError(null);
    setQuote(null);
    try {
      const response = await fetch(`${api.shipping}/quote?country=${encodeURIComponent(country)}`);
      if (!response.ok) {
        throw new Error(`Shipping quote failed: ${response.status}`);
      }
      setQuote(await response.json());
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unable to fetch shipping quote');
    }
  }

  async function loadTracking() {
    setError(null);
    setTracking(null);
    try {
      const response = await fetch(`${api.shipping}/${encodeURIComponent(trackingId)}`);
      if (!response.ok) {
        throw new Error(`Shipping track failed: ${response.status}`);
      }
      setTracking(await response.json());
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unable to fetch shipping tracking');
    }
  }

  return (
    <div className="content">
      <h1 className="text-2xl font-semibold">Shipping Service</h1>
      <p className="text-gray-600">Request a shipping quote and track an order.</p>
      {error && <p className="text-red-600">{error}</p>}
      <section className="mt-6">
        <h2 className="font-medium">Shipping Quote</h2>
        <label className="block">
          Country:<br />
          <input value={country} onChange={(e) => setCountry(e.target.value)} className="w-full p-2 border rounded" />
        </label>
        <button onClick={loadQuote} className="btn mt-3">Get Quote</button>
        {quote && <pre className="mt-3">{JSON.stringify(quote, null, 2)}</pre>}
      </section>
      <section className="mt-6">
        <h2 className="font-medium">Track Shipping</h2>
        <label className="block">
          Order ID:<br />
          <input value={trackingId} onChange={(e) => setTrackingId(e.target.value)} className="w-full p-2 border rounded" />
        </label>
        <button onClick={loadTracking} className="btn mt-3">Track Order</button>
        {tracking && <pre className="mt-3">{JSON.stringify(tracking, null, 2)}</pre>}
      </section>
    </div>
  );
}
