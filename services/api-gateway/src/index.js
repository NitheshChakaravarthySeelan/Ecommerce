/**
 * API Gateway (Express.js + http-proxy)
 *
 * The single entry point for all external traffic. Every request goes
 * through this gateway, which handles:
 *
 * 1. CORS (all origins allowed)
 * 2. Distributed trace ID propagation (x-trace-id header)
 * 3. JWT authentication (with skip-list for /auth/register and /auth/login)
 * 4. Reverse-proxying to the appropriate backend microservice
 * 5. Adding x-user-id and x-trace-id to proxied requests
 *
 * Route structure:
 *   /api/*       → business endpoints (require JWT)
 *   /auth/*      → authentication endpoints (public)
 *   /api/health/* → per-service health checks (require JWT)
 *
 * All backend URLs are hardcoded in the SERVICES map. In production,
 * these should come from service discovery (Consul, K8s DNS, etc.).
 */

const express = require('express');
const cors = require('cors');
const httpProxy = require('http-proxy');
const jwt = require('jsonwebtoken');
const { v4: uuidv4 } = require('uuid');

const app = express();
const JWT_SECRET = process.env.JWT_SECRET;
if (!JWT_SECRET || JWT_SECRET.length < 32) {
    console.error('FATAL: JWT_SECRET environment variable must be set and at least 32 characters');
    process.exit(1);
}

// ── Global middleware ─────────────────────────────────────────────

app.use(cors());
app.use(express.json());

/**
 * Trace ID middleware.
 *
 * Reads an existing x-trace-id from the incoming request (propagated
 * from the frontend/other services) or generates a new UUID. Attaches
 * it to both the request object and the response header so the trace
 * ID flows through the entire request chain.
 */
app.use((req, res, next) => {
  req.traceId = req.headers['x-trace-id'] || uuidv4();
  res.setHeader('x-trace-id', req.traceId);
  console.log(JSON.stringify({
    timestamp: new Date().toISOString(),
    traceId: req.traceId,
    method: req.method,
    path: req.path,
    type: 'REQUEST',
  }));
  next();
});

// ── Service registry ─────────────────────────────────────────────

const SERVICES = {
  catalog: 'http://localhost:8084',
  inventory: 'http://localhost:8085',
  cart: 'http://localhost:8086',
  order: 'http://localhost:8087',
  payment: 'http://localhost:8088',
  auth: 'http://localhost:8081',
  saga: 'http://localhost:8083',
  shipping: 'http://localhost:8082',
  orchestrator: 'http://localhost:8089',
};

// ── JWT authentication ───────────────────────────────────────────

/**
 * JWT verification middleware.
 *
 * Skips authentication for /auth/register and /auth/login.
 * For all other routes, extracts the Bearer token from the
 * Authorization header and verifies it against JWT_SECRET.
 *
 * Returns 401 if no token is provided, 403 if the token is invalid.
 */
const authenticateToken = (req, res, next) => {
  if (req.path === '/auth/register' || req.path === '/auth/login') {
    return next();
  }

  const authHeader = req.headers['authorization'];
  const token = authHeader && authHeader.split(' ')[1];

  if (!token) {
    return res.status(401).json({
      error: 'Unauthorized',
      traceId: req.traceId,
    });
  }

  try {
    const decoded = jwt.verify(token, JWT_SECRET);
    req.user = decoded;
    next();
  } catch (err) {
    return res.status(403).json({
      error: 'Forbidden',
      traceId: req.traceId,
    });
  }
};

app.use(authenticateToken);

// ── Proxy handler factory ────────────────────────────────────────

/**
 * Create an Express handler that proxies requests to a backend service.
 *
 * Features:
 * - Forwards x-trace-id and x-user-id to the backend
 * - Returns 503 with the trace ID if the backend is unreachable
 * - Uses http-proxy with changeOrigin (rewrites Host header)
 *
 * @param {string} target — backend base URL (e.g. 'http://localhost:8084')
 * @returns {Function} Express request handler
 */
const createProxyHandler = (target) => {
  const proxy = httpProxy.createProxyServer({
    target,
    changeOrigin: true,
  });

  proxy.on('error', (err, req, res) => {
    console.error(JSON.stringify({
      timestamp: new Date().toISOString(),
      traceId: req.traceId,
      type: 'PROXY_ERROR',
      error: err.message,
    }));
    res.status(503).json({
      error: 'Service unavailable',
      traceId: req.traceId,
    });
  });

  proxy.on('proxyReq', (proxyReq, req) => {
    proxyReq.setHeader('x-trace-id', req.traceId);
    proxyReq.setHeader('x-user-id', req.user?.id || 'anonymous');
  });

  return (req, res) => proxy.web(req, res);
};

// ── Route definitions ────────────────────────────────────────────

// Catalog
app.get('/api/products', createProxyHandler(SERVICES.catalog));
app.post('/api/products', createProxyHandler(SERVICES.catalog));
app.get('/api/products/:id', createProxyHandler(SERVICES.catalog));

// Inventory
app.get('/api/inventory', createProxyHandler(SERVICES.inventory));
app.post('/api/inventory/reserve', createProxyHandler(SERVICES.inventory));
app.post('/api/inventory/release', createProxyHandler(SERVICES.inventory));
app.post('/api/inventory/batch-reserve', createProxyHandler(SERVICES.inventory));
app.post('/api/inventory/batch-release', createProxyHandler(SERVICES.inventory));
app.get('/api/inventory/:productId', createProxyHandler(SERVICES.inventory));

// Cart
app.get('/api/cart/:userId', createProxyHandler(SERVICES.cart));
app.post('/api/cart/:userId/items', createProxyHandler(SERVICES.cart));
app.get('/api/cart/:userId/items', createProxyHandler(SERVICES.cart));
app.patch('/api/cart/:userId/items/:itemId', createProxyHandler(SERVICES.cart));
app.delete('/api/cart/:userId/items/:itemId', createProxyHandler(SERVICES.cart));

// Orders
app.post('/api/orders', createProxyHandler(SERVICES.order));
app.get('/api/orders', createProxyHandler(SERVICES.order));
app.get('/api/orders/:orderId', createProxyHandler(SERVICES.order));

// Payments
app.post('/api/payments', createProxyHandler(SERVICES.payment));
app.get('/api/payments/:paymentId', createProxyHandler(SERVICES.payment));

// Shipping
app.get('/api/shipping/quote', createProxyHandler(SERVICES.shipping));
app.post('/api/shipping/:orderId', createProxyHandler(SERVICES.shipping));
app.get('/api/shipping/:orderId', createProxyHandler(SERVICES.shipping));

// Auth (no JWT — these routes are excluded by authenticateToken)
app.post('/auth/register', createProxyHandler(SERVICES.auth));
app.post('/auth/login', createProxyHandler(SERVICES.auth));

// Saga
app.post('/api/saga/start', createProxyHandler(SERVICES.saga));
app.get('/api/saga/:sagaId', createProxyHandler(SERVICES.saga));

// ── Health checks ────────────────────────────────────────────────

// Gateway self-check
app.get('/health', (req, res) => {
  res.json({
    status: 'ok',
    timestamp: new Date().toISOString(),
    traceId: req.traceId,
  });
});

// Per-service health probes (proxied)
app.get('/api/health/auth', createProxyHandler(SERVICES.auth));
app.get('/api/health/catalog', createProxyHandler(SERVICES.catalog));
app.get('/api/health/inventory', createProxyHandler(SERVICES.inventory));
app.get('/api/health/cart', createProxyHandler(SERVICES.cart));
app.get('/api/health/orders', createProxyHandler(SERVICES.order));
app.get('/api/health/payments', createProxyHandler(SERVICES.payment));
app.get('/api/health/shipping', createProxyHandler(SERVICES.shipping));
app.get('/api/health/orchestrator', createProxyHandler(SERVICES.orchestrator));
app.get('/api/health/saga', createProxyHandler(SERVICES.saga));

// ── Error handling ───────────────────────────────────────────────

/**
 * Global error handler — catches unhandled errors from all middleware
 * and routes. Returns a 500 with the trace ID for debugging.
 */
app.use((err, req, res, next) => {
  console.error(JSON.stringify({
    timestamp: new Date().toISOString(),
    traceId: req.traceId,
    type: 'ERROR',
    error: err.message,
    stack: err.stack,
  }));
  res.status(500).json({
    error: 'Internal server error',
    traceId: req.traceId,
  });
});

/**
 * 404 catch-all — any route that wasn't matched above returns this.
 */
app.use((req, res) => {
  res.status(404).json({
    error: 'Not found',
    traceId: req.traceId,
  });
});

// ── Server start ─────────────────────────────────────────────────

const PORT = process.env.PORT || 3001;
app.listen(PORT, () => {
  console.log(`API Gateway listening on port ${PORT}`);
});
