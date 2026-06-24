package com.ecommerce.saga.repository;

import com.ecommerce.saga.entity.SagaStateEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SagaStateRepository extends JpaRepository<SagaStateEntity, Long> {
    SagaStateEntity findBySagaId(String sagaId);
}
