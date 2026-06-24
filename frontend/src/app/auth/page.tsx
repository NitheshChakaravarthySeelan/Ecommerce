"use client";

import { useState } from 'react';
import { useAuth } from '../../lib/auth';

export default function AuthPage() {
  const { user, token, login, register, logout, getAuthHeaders } = useAuth();
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [email, setEmail] = useState('customer@ecommerce.local');
  const [password, setPassword] = useState('changeme');

  async function handleLogin() {
    setMessage(null);
    setError(null);
    try {
      await login(email, password);
      setMessage('Logged in successfully');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Login failed');
    }
  }

  async function handleRegister() {
    setMessage(null);
    setError(null);
    try {
      await register(email, password);
      setMessage('Registered successfully');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Registration failed');
    }
  }

  return (
    <div className="content">
      <h1 className="text-2xl font-semibold">Auth</h1>
      <p className="text-gray-600">Register, login, and manage your session.</p>
      {error && <p className="text-red-600">{error}</p>}
      {message && <p className="text-green-600">{message}</p>}

      {user ? (
        <div className="mt-6">
          <div className="page-card">
            <p><strong>ID:</strong> {user.id}</p>
            <p><strong>Email:</strong> {user.email}</p>
            <p><strong>Role:</strong> {user.role}</p>
            <p className="text-xs text-gray-500 mt-2 break-all">
              <strong>Token:</strong> {token.substring(0, 40)}...
            </p>
          </div>
          <button onClick={logout} className="btn mt-4">Logout</button>
        </div>
      ) : (
        <div className="mt-6">
          <label className="block">
            Email:<br />
            <input value={email} onChange={(e) => setEmail(e.target.value)} className="w-full p-2 border rounded" />
          </label>
          <label className="block mt-4">
            Password:<br />
            <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} className="w-full p-2 border rounded" />
          </label>
          <div className="flex gap-3 mt-4">
            <button onClick={handleLogin} className="btn">Login</button>
            <button onClick={handleRegister} className="btn">Register</button>
          </div>
        </div>
      )}
    </div>
  );
}
