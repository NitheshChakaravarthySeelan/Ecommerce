"use client";

import { useState } from 'react';
import { api } from '../../lib/api';

interface SagaResponse {
  sagaId: string;
  status: string;
  payload: Record<string, unknown>;
}

export default function SagaPage() {
  const [sagaId, setSagaId] = useState('');
  const [status, setStatus] = useState<SagaResponse | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function startSaga() {
    setError(null);
    setStatus(null);
    try {
      const response = await fetch(api.saga, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ orderId: 'sample-order', amount: 129.99 }),
      });
      if (!response.ok) {
        throw new Error(`Saga start failed: ${response.status}`);
      }
      setStatus(await response.json());
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unable to start saga');
    }
  }

  async function loadSaga() {
    if (!sagaId) {
      setError('Enter a saga ID first');
      return;
    }
    setError(null);
    setStatus(null);
    try {
      const response = await fetch(`${api.saga}/${encodeURIComponent(sagaId)}`);
      if (!response.ok) {
        throw new Error(`Saga status failed: ${response.status}`);
      }
      setStatus(await response.json());
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unable to load saga status');
    }
  }

  return (
    <div className="content">
      <h1 className="text-2xl font-semibold">Saga Service</h1>
      <p className="text-gray-600">Start a saga and inspect its status.</p>
      {error && <p className="text-red-600">{error}</p>}
      <button onClick={startSaga} className="btn">Start Sample Saga</button>
      {status && (
        <div className="mt-3">
          <p>Started saga {status.sagaId}</p>
          <pre>{JSON.stringify(status, null, 2)}</pre>
        </div>
      )}
      <div className="mt-6">
        <label className="block">
          Saga ID:<br />
          <input value={sagaId} onChange={(e) => setSagaId(e.target.value)} className="w-full p-2 border rounded" />
        </label>
        <button onClick={loadSaga} className="btn mt-3">Load Saga Status</button>
      </div>
    </div>
  );
}
