# Ecommerce Platform Blueprint

This repository contains a full end-to-end ecommerce platform scaffold using Java, Rust, Python, Kafka, Saga orchestration, and a React/TypeScript frontend.

## Architecture
- `services/auth-java`: Java Spring Boot auth service
- `services/shipping-java`: Java Spring Boot shipping and fulfillment service
- `services/saga-java`: Java Spring Boot saga orchestrator service
- `services/catalog-rust`: Rust catalog service with product APIs
- `services/inventory-rust`: Rust inventory and reservation service
- `services/cart-python`: Python FastAPI shopping cart service
- `services/order-python`: Python FastAPI order service
- `services/payment-python`: Python FastAPI payment service
- `frontend`: Next.js + TypeScript storefront
- `docker-compose.yml`: local development environment
- `ARCHITECTURE.md`: HLD and LLD design documentation

## Getting Started
1. Install Docker and Docker Compose.
2. Copy `.env.example` to `.env` and review service ports.
3. Run:
   ```bash
   docker compose up --build
   ```
4. Open the storefront at `http://localhost:3000`.

## Service Ports
- Frontend: `3000`
- Auth: `8081`
- Shipping: `8082`
- Saga: `8083`
- Catalog: `8084`
- Inventory: `8085`
- Cart: `8086`
- Order: `8087`
- Payment: `8088`
- Kafka: `9092`
- PostgreSQL: `5432`
- Redis: `6379`

## Notes
This scaffold is designed with HLD/LLD principles in mind. Each service includes a minimal runnable implementation and can be extended into a production-ready system.
