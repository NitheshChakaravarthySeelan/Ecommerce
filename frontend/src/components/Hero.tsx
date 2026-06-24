export default function Hero() {
  return (
    <section className="hero-outer">
      <div className="hero-panel">
        <div className="hero-inner">
          <div className="hero-left">
            <h1 className="text-5xl font-serif leading-tight mb-6">Find perfect plants for your home</h1>
            <p className="text-gray-600 mb-6">Beautiful plants that encourage you to get creative.</p>
            <a href="/catalog" className="inline-block px-6 py-3 bg-black text-white rounded-md">SHOP NOW</a>
          </div>
          <div className="hero-right">
            <img src="https://images.unsplash.com/photo-1524594154900-4d3b45f9b0c2?auto=format&fit=crop&w=800&q=80" alt="Plants" className="w-80 rounded-xl shadow-lg object-cover" />
          </div>
        </div>
      </div>
    </section>
  );
}
