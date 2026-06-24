"use client";

import Link from 'next/link';
import { usePathname } from 'next/navigation';

const navLinks = [
  { href: '/catalog', label: 'Catalog' },
  { href: '/cart', label: 'Cart' },
  { href: '/orders', label: 'Orders' },
  { href: '/admin', label: 'Admin' },
];

export default function NavBar() {
  const pathname = usePathname();

  return (
    <header className="site-header">
      <div className="site-container nav-inner">
        <div className="flex items-center gap-6 flex-wrap">
          <Link href="/" className="brand">Ecommerce</Link>
          <nav className="nav-links">
            {navLinks.map((link) => (
              <Link
                key={link.href}
                href={link.href}
                className={`text-sm ${pathname === link.href ? 'text-blue-600 font-semibold' : 'text-gray-700 hover:text-gray-900'}`}
              >
                {link.label}
              </Link>
            ))}
          </nav>
        </div>
        <div className="flex items-center gap-4 flex-wrap">
          <Link href="/auth" className="text-sm text-gray-700 hover:text-gray-900">Sign in</Link>
          <Link href="/checkout" className="btn">Checkout</Link>
        </div>
      </div>
    </header>
  );
}
