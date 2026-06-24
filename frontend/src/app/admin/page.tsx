"use client";

import { FormEvent, useState } from 'react';
import { api } from '../../lib/api';

const categories = [
  'Indoor Plants',
  'Outdoor Plants',
  'Planters & Care',
  'Accessories',
];

interface ProductCreate {
  name: string;
  description: string;
  category: string;
  image_url: string;
  price: number;
  stock: number;
  in_stock: boolean;
}

export default function AdminPage() {
  const [product, setProduct] = useState<ProductCreate>({
    name: '',
    description: '',
    category: categories[0],
    image_url: 'https://images.unsplash.com/photo-1519710164239-da123dc03ef4?auto=format&fit=crop&w=800&q=80',
    price: 0,
    stock: 0,
    in_stock: true,
  });
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setLoading(true);
    setMessage(null);
    setError(null);

    try {
      const response = await fetch(api.catalog, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(product),
      });

      if (!response.ok) {
        throw new Error(`Product creation failed: ${response.status}`);
      }

      const created = await response.json();
      setMessage(`Created product ${created.name}`);
      setProduct({
        name: '',
        description: '',
        category: categories[0],
        image_url: 'https://images.unsplash.com/photo-1519710164239-da123dc03ef4?auto=format&fit=crop&w=800&q=80',
        price: 0,
        stock: 0,
        in_stock: true,
      });
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unable to create product');
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="site-container">
      <div className="page-panel max-w-3xl mx-auto">
        <h1 className="page-title">Admin: Add product</h1>
        <p className="page-subtitle">Create new catalog items with image URL, price, category, and stock.</p>

        {message && <p className="text-green-600">{message}</p>}
        {error && <p className="text-red-600">{error}</p>}

        <form onSubmit={handleSubmit} className="space-y-6">
          <div className="grid gap-6 md:grid-cols-2">
            <label className="block">
              <span className="text-sm font-medium">Product name</span>
              <input
                value={product.name}
                onChange={(event) => setProduct({ ...product, name: event.target.value })}
                placeholder="Monstera Deliciosa"
                className="mt-2 block w-full rounded-2xl border border-slate-200 bg-white px-4 py-3"
                required
              />
            </label>
            <label className="block">
              <span className="text-sm font-medium">Category</span>
              <select
                value={product.category}
                onChange={(event) => setProduct({ ...product, category: event.target.value })}
                className="mt-2 block w-full rounded-2xl border border-slate-200 bg-white px-4 py-3"
              >
                {categories.map((category) => (
                  <option key={category} value={category}>
                    {category}
                  </option>
                ))}
              </select>
            </label>
          </div>

          <label className="block">
            <span className="text-sm font-medium">Description</span>
            <textarea
              value={product.description}
              onChange={(event) => setProduct({ ...product, description: event.target.value })}
              rows={4}
              placeholder="A lush statement plant with dramatic split leaves."
              className="mt-2 block w-full rounded-2xl border border-slate-200 bg-white px-4 py-3"
              required
            />
          </label>

          <label className="block">
            <span className="text-sm font-medium">Image URL</span>
            <input
              value={product.image_url}
              onChange={(event) => setProduct({ ...product, image_url: event.target.value })}
              placeholder="https://example.com/product.jpg"
              className="mt-2 block w-full rounded-2xl border border-slate-200 bg-white px-4 py-3"
              required
            />
          </label>

          <div className="grid gap-6 md:grid-cols-3">
            <label className="block">
              <span className="text-sm font-medium">Price</span>
              <input
                type="number"
                step="0.01"
                min="0"
                value={product.price}
                onChange={(event) => setProduct({ ...product, price: Number(event.target.value) })}
                className="mt-2 block w-full rounded-2xl border border-slate-200 bg-white px-4 py-3"
                required
              />
            </label>
            <label className="block">
              <span className="text-sm font-medium">Stock</span>
              <input
                type="number"
                min="0"
                value={product.stock}
                onChange={(event) => setProduct({ ...product, stock: Number(event.target.value) })}
                className="mt-2 block w-full rounded-2xl border border-slate-200 bg-white px-4 py-3"
                required
              />
            </label>
            <label className="block">
              <span className="text-sm font-medium">In stock</span>
              <select
                value={product.in_stock ? 'true' : 'false'}
                onChange={(event) => setProduct({ ...product, in_stock: event.target.value === 'true' })}
                className="mt-2 block w-full rounded-2xl border border-slate-200 bg-white px-4 py-3"
              >
                <option value="true">Yes</option>
                <option value="false">No</option>
              </select>
            </label>
          </div>

          <button type="submit" className="btn w-full" disabled={loading}>
            {loading ? 'Saving…' : 'Save product'}
          </button>
        </form>
      </div>
    </div>
  );
}
