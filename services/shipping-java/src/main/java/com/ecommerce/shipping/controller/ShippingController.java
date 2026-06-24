package com.ecommerce.shipping.controller;

import com.ecommerce.shipping.entity.ShippingEntity;
import com.ecommerce.shipping.repository.ShippingRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for shipping operations.
 *
 * <p>Provides endpoints for quoting, creating, and tracking shipments.
 * The saga-based shipping dispatch flows through {@code ShippingSagaListener}
 * (Kafka consumer), not through these REST endpoints.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>GET  /shipping/quote          — get a shipping estimate (hardcoded)</li>
 *   <li>POST /shipping/{orderId}      — create a shipment record</li>
 *   <li>GET  /shipping/{orderId}      — track a shipment</li>
 * </ul>
 */
@RestController
public class ShippingController {

    private final ShippingRepository shippingRepository;
    private final int estimatedDeliveryDays;
    private final double shippingCost;

    public ShippingController(ShippingRepository shippingRepository,
                              @Value("${shipping.estimated-days:4}") int estimatedDeliveryDays,
                              @Value("${shipping.cost:12.50}") double shippingCost) {
        this.shippingRepository = shippingRepository;
        this.estimatedDeliveryDays = estimatedDeliveryDays;
        this.shippingCost = shippingCost;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of("status", "ok", "service", "shipping"));
    }

    /**
     * Return a shipping cost and delivery estimate.
     *
     * <p>Currently returns hardcoded values. In production this would
     * query a carrier API for zone-based, weight-based pricing.
     *
     * @param country the destination country (default: "US")
     * @return estimated cost and delivery days
     */
    @GetMapping("/shipping/quote")
    public ResponseEntity<Map<String, Object>> quote(@RequestParam(name = "country", defaultValue = "US") String country) {
        return ResponseEntity.ok(Map.of(
            "country", country,
            "estimatedDeliveryDays", estimatedDeliveryDays,
            "cost", shippingCost
        ));
    }

    /**
     * Create or retrieve a shipment record for an order.
     *
     * <p>If a shipment already exists for the given order, it is returned
     * as-is (idempotent). Otherwise a new PENDING shipment is created.
     *
     * @param orderId the order to ship
     * @return shipment details including status and estimated delivery
     */
    @PostMapping("/shipping/{orderId}")
    public ResponseEntity<Map<String, Object>> createShipment(@PathVariable(name = "orderId") String orderId) {
        ShippingEntity entity = shippingRepository.findByOrderId(orderId);
        if (entity == null) {
            entity = new ShippingEntity();
            entity.setOrderId(orderId);
            entity.setStatus("PENDING");
            entity.setEstimatedDelivery("2026-06-01");
            entity = shippingRepository.save(entity);
        }

        return ResponseEntity.ok(Map.of(
            "orderId", entity.getOrderId(),
            "status", entity.getStatus(),
            "estimatedDelivery", entity.getEstimatedDelivery()
        ));
    }

    /**
     * Track a shipment by order ID.
     *
     * @param orderId the order to look up
     * @return 200 with shipment details, or 404 if not found
     */
    @GetMapping("/shipping/{orderId}")
    public ResponseEntity<Map<String, Object>> track(@PathVariable(name = "orderId") String orderId) {
        ShippingEntity entity = shippingRepository.findByOrderId(orderId);
        if (entity != null) {
            return ResponseEntity.ok(Map.of(
                "orderId", entity.getOrderId(),
                "status", entity.getStatus(),
                "estimatedDelivery", entity.getEstimatedDelivery()
            ));
        } else {
            return ResponseEntity.notFound().build();
        }
    }
}
