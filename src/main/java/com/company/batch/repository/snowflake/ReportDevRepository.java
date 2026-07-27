package com.company.batch.repository.snowflake;

import com.company.batch.entity.snowflake.ReportDev;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for Snowflake ReportDev entity
 * 
 * This repository is automatically configured to use the Snowflake datasource
 * via the @EnableJpaRepositories annotation in WarehouseConfig.
 * 
 * Provides CRUD operations and custom queries for order status tracking.
 */
@Repository
public interface ReportDevRepository extends JpaRepository<ReportDev, Long> {
    
    /**
     * Find the most recent status for a given order ID
     * Useful for checking if an order already exists in Snowflake
     */
    @Query("SELECT r FROM ReportDev r WHERE r.orderId = :orderId ORDER BY r.processedAt DESC")
    Optional<ReportDev> findLatestByOrderId(@Param("orderId") String orderId);
    
    /**
     * Check if an order exists with a specific status
     */
    boolean existsByOrderIdAndOrderStatus(String orderId, String orderStatus);
    
    /**
     * Count orders by status
     * Useful for monitoring and reporting
     */
    long countByOrderStatus(String orderStatus);
}

// Made with Bob
