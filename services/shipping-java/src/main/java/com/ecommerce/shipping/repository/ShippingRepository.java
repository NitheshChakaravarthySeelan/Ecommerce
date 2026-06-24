package com.ecommerce.shipping.repository;

import com.ecommerce.shipping.entity.ShippingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShippingRepository extends JpaRepository<ShippingEntity, Long> {
    ShippingEntity findByOrderId(String orderId);
}
