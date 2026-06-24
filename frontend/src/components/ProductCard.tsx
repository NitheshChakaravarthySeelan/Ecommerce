// frontend/src/components/ProductCard.tsx
import Image from 'next/image';

export interface Product {
  id: string;
  name: string;
  description: string;
  price: number;
  image_url: string;
  category: string;
  stock: number;
  in_stock: boolean;
}

export default function ProductCard({ product }: { product: Product }) {
  return (
    <div className="max-w-sm rounded-xl overflow-hidden shadow-lg bg-white dark:bg-gray-800 transition-transform hover:scale-105">
      <div className="relative h-48 w-full">
        <Image
          src={product.image_url}
          alt={product.name}
          layout="fill"
          objectFit="cover"
          className="rounded-t-xl"
        />
      </div>
      <div className="p-4">
        <h2 className="font-semibold text-lg text-gray-900 dark:text-gray-100">{product.name}</h2>
        <p className="text-sm text-gray-600 dark:text-gray-300 mt-1">{product.category}</p>
        <p className="mt-2 text-gray-800 dark:text-gray-200 text-sm line-clamp-3">{product.description}</p>
        <div className="mt-4 flex justify-between items-center">
          <span className="font-bold text-xl text-indigo-600 dark:text-indigo-400">${product.price.toFixed(2)}</span>
          <button
            disabled={!product.in_stock}
            className={`px-3 py-1 rounded ${product.in_stock ? 'bg-indigo-600 hover:bg-indigo-700 text-white' : 'bg-gray-400 text-gray-700 cursor-not-allowed'} transition-colors`}
          >
            {product.in_stock ? 'Add to Cart' : 'Out of Stock'}
          </button>
        </div>
      </div>
    </div>
  );
}
