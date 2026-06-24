"use client";

import Link from 'next/link';
import { useEffect, useState } from 'react';
import { api } from '../../lib/api';

interface Product {
  id: string;
  name: string;
  description: string;
  category: string;
  image_url: string;
  price: number;
  stock: number;
  in_stock: boolean;
}

const categories = [
  'All',
  'Indoor Plants',
  'Outdoor Plants',
  'Planters & Care',
  'Accessories',
];

export default function CatalogPage() {
  const [products, setProducts] = useState<Product[]>([]);
  const [category, setCategory] = useState('All');
  const [loading, setLoading] = useState(true);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    async function loadProducts() {
      setLoading(true);
      setError(null);
      try {
        const query = category !== 'All' ? `?category=${encodeURIComponent(category)}` : '';
        const response = await fetch(`${api.catalog}${query}`);
        if (!response.ok) {
          throw new Error(`Catalog request failed: ${response.status}`);
        }
        const data = await response.json();
        setProducts(data);
      } catch (err) {
        setError(err instanceof Error ? err.message : 'Unable to load products');
      } finally {
        setLoading(false);
      }
    }

    loadProducts();
  }, [category]);

  async function addToCart(product: Product) {
    setMessage(null);
    setError(null);

    try {
      const response = await fetch(`${api.cart('user123')}/items`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          product_id: product.id,
          quantity: 1,
          price: product.price,
        }),
      });
      if (!response.ok) {
        throw new Error(`Cart request failed: ${response.status}`);
      }
      setMessage(`${product.name} added to cart.`);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unable to add to cart');
    }
  }

  return (
    <div className="site-container">
      <div className="page-panel">
        <div>
          <h1 className="page-title">Catalog</h1>
          <p className="page-subtitle">Browse beautifully curated plant products with real images, stock status, and category filters.</p>

          <div className="page-tabs">
            {categories.map((item) => (
              <button
                key={item}
                type="button"
                onClick={() => setCategory(item)}
                className={`category-pill ${category === item ? 'active' : ''}`}
              >
                {item}
              </button>
            ))}
          </div>

          {loading && <p>Loading products…</p>}
          {error && <p className="text-red-600">{error}</p>}
          {message && <p className="text-green-600">{message}</p>}

          <div className="catalog-grid">
            {products.length === 0 && !loading ? (
              <div className="page-card col-span-3">No products match that category.</div>
            ) : (
              products.map((product) => (
                <div key={product.id} className="page-card">
                  <img src={product.image_url} alt={product.name} className="product-card-img" />
                  <div className="mb-4">
                    <h2 className="text-xl font-semibold">{product.name}</h2>
                    <p className="text-sm text-gray-600 mt-2">{product.description}</p>
                  </div>
                  <div className="flex flex-wrap items-center justify-between gap-3">
                    <p className="text-lg font-semibold text-slate-900">${product.price.toFixed(2)}</p>
                    <span className={`text-sm rounded-full px-3 py-1 ${product.in_stock ? 'bg-green-100 text-green-800' : 'bg-red-100 text-red-800'}`}>
                      {product.in_stock ? `In stock (${product.stock})` : 'Out of stock'}
                    </span>
                  </div>
                  <div className="links mt-6 flex-wrap">
                    <button
                      type="button"
                      disabled={!product.in_stock}
                      onClick={() => addToCart(product)}
                      className="btn w-full justify-center"
                    >
                      Add to cart
                    </button>
                    <Link href={`/catalog/${product.id}`} className="btn-secondary w-full text-center">
                      View details
                    </Link>
                  </div>
                </div>
              ))
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
