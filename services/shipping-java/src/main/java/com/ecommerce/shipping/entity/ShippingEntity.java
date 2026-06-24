package com.ecommerce.shipping.entity;

import jakarta.persistence.*;

/**
 * Persistent shipment record for an order.
 *
 * <p>Each order has exactly one shipment (identified by orderId).
 * Created by either the REST endpoint (POST /shipping/{orderId})
 * or the saga listener when it receives a shipping-initiated event.
 */
@Entity
@Table(name = "shipping")
public class ShippingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String orderId;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private String estimatedDelivery;

    public ShippingEntity() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getEstimatedDelivery() { return estimatedDelivery; }
    public void setEstimatedDelivery(String estimatedDelivery) { this.estimatedDelivery = estimatedDelivery; }
}
