"use client";

import { useEffect, useMemo, useState } from 'react';
import Link from 'next/link';
import { api } from '../../lib/api';

interface CartItem {
  item_id: string;
  product_id: string;
  quantity: number;
  price: number;
}

interface Product {
  id: string;
  name: string;
  image_url: string;
  price: number;
}

interface CartProduct extends CartItem {
  name: string;
  image_url: string;
}

const userId = 'user123';

export default function CartPage() {
  const [cartItems, setCartItems] = useState<CartItem[]>([]);
  const [products, setProducts] = useState<Record<string, Product>>({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    async function loadCart() {
      setLoading(true);
      setError(null);
      try {
        const [cartResp, productsResp] = await Promise.all([
          fetch(`${api.cart(userId)}/items`),
          fetch(api.catalog),
        ]);

        if (!cartResp.ok) throw new Error('Failed to load cart');
        if (!productsResp.ok) throw new Error('Failed to load products');

        const cartData: CartItem[] = await cartResp.json();
        const productsData: Product[] = await productsResp.json();
        const productMap = Object.fromEntries(productsData.map((product) => [product.id, product]));

        setCartItems(cartData);
        setProducts(productMap);
      } catch (err) {
        setError(err instanceof Error ? err.message : 'Unable to load cart');
      } finally {
        setLoading(false);
      }
    }

    loadCart();
  }, []);

  const cartProducts: CartProduct[] = useMemo(
    () =>
      cartItems.map((item) => ({
        ...item,
        name: products[item.product_id]?.name || 'Product',
        image_url: products[item.product_id]?.image_url || '/placeholder.png',
      })),
    [cartItems, products],
  );

  const total = useMemo(
    () => cartProducts.reduce((sum, item) => sum + item.price * item.quantity, 0),
    [cartProducts],
  );

  async function updateQuantity(itemId: string, quantity: number) {
    if (quantity < 1) return;
    setLoading(true);
    setError(null);

    try {
      const response = await fetch(`http://localhost:8086/cart/${userId}/items/${itemId}`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ quantity }),
      });
      if (!response.ok) throw new Error('Unable to update quantity');
      const updatedCart: CartItem[] = await response.json();
      setCartItems(updatedCart);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unable to update cart');
    } finally {
      setLoading(false);
    }
  }

  async function removeItem(itemId: string) {
    setLoading(true);
    setError(null);

    try {
      const response = await fetch(`${api.cart(userId)}/items/${itemId}`, {
        method: 'DELETE',
      });
      if (!response.ok) throw new Error('Unable to remove item');
      const updatedCart: CartItem[] = await response.json();
      setCartItems(updatedCart);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unable to remove item');
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="site-container">
      <div className="page-panel">
        <h1 className="page-title">Your cart</h1>
        <p className="page-subtitle">Review your items before checkout and update quantities on the fly.</p>

        {loading && <p>Loading cart…</p>}
        {error && <p className="text-red-600">{error}</p>}

        {cartProducts.length === 0 && !loading ? (
          <div className="page-card">
            <p>Your cart is empty.</p>
            <Link href="/catalog" className="btn mt-4 inline-block">
              Browse collections
            </Link>
          </div>
        ) : (
          <div className="grid gap-8 xl:grid-cols-[1.7fr_0.8fr]">
            <div className="space-y-6">
              {cartProducts.map((item) => (
                <div key={item.product_id} className="page-card flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
                  <div className="flex gap-4">
                    <img src={item.image_url} alt={item.name} className="w-28 h-28 rounded-[1.5rem] object-cover" />
                    <div>
                      <h2 className="text-lg font-semibold">{item.name}</h2>
                      <p className="text-sm text-gray-600">${item.price.toFixed(2)} each</p>
                    </div>
                  </div>
                  <div className="flex flex-wrap items-center gap-3">
                    <div className="flex items-center gap-2 rounded-full border border-slate-200 bg-white px-3 py-2">
                      <button type="button" onClick={() => updateQuantity(item.item_id, item.quantity - 1)} className="text-xl">−</button>
                      <span className="min-w-[2rem] text-center">{item.quantity}</span>
                      <button type="button" onClick={() => updateQuantity(item.item_id, item.quantity + 1)} className="text-xl">+</button>
                    </div>
                    <button type="button" onClick={() => removeItem(item.item_id)} className="btn-secondary">
                      Remove
                    </button>
                  </div>
                </div>
              ))}
            </div>

            <div className="page-summary">
              <h2 className="text-xl font-semibold mb-4">Order summary</h2>
              <div className="space-y-3">
                <div className="flex justify-between text-sm text-gray-600">
                  <span>Item total</span>
                  <span>${total.toFixed(2)}</span>
                </div>
                <div className="flex justify-between text-sm text-gray-600">
                  <span>Shipping</span>
                  <span>$9.99</span>
                </div>
                <div className="flex justify-between text-base font-semibold">
                  <span>Estimated total</span>
                  <span>${(total + 9.99).toFixed(2)}</span>
                </div>
              </div>
              <Link href="/checkout" className="btn mt-6 w-full text-center">
                Checkout
              </Link>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
