package com.company.batch.repository.local;

import com.company.batch.entity.local.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for local OrderStatus entity
 * 
 * This repository uses the primary datasource for comparison operations.
 */
@Repository
public interface OrderStatusRepository extends JpaRepository<OrderStatus, Long> {
    
    /**
     * Find order status by order ID
     */
    Optional<OrderStatus> findByOrderId(String orderId);
    
    /**
     * Check if order exists
     */
    boolean existsByOrderId(String orderId);
}

// Made with Bob
