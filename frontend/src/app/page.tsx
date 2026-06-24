import Hero from '../components/Hero';

export default function Home() {
  return (
    <main>
      <Hero />

      <section className="site-container mt-12">
        <h2 className="text-2xl text-center font-serif mb-6">Categories</h2>
        <div className="grid grid-cols-3 gap-6">
          <div className="bg-white rounded-xl p-6 shadow">Indoor Plants</div>
          <div className="bg-white rounded-xl p-6 shadow">Outdoor Plants</div>
          <div className="bg-white rounded-xl p-6 shadow">Planters & Care</div>
        </div>
      </section>
    </main>
  );
}
