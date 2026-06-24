import './globals.css';
import type { Metadata } from 'next';
import NavBar from '../components/NavBar';
import { AuthProvider } from '../lib/auth';

export const metadata: Metadata = {
  title: 'Ecommerce Frontend',
  description: 'Ecommerce platform frontend for microservices demo',
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body>
        <AuthProvider>
          <NavBar />
          <main>
            <div className="site-container">
              {children}
            </div>
          </main>
        </AuthProvider>
      </body>
    </html>
  );
}
