package com.company.batch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main Application Class for Order Status Batch Processing
 * 
 * This Spring Boot application processes order status files and syncs
 * changed orders to Snowflake using Key Pair authentication.
 * 
 * Features:
 * - Multi-datasource configuration (local + Snowflake)
 * - Spring Batch for file processing
 * - JPA for database operations
 * - Scheduled execution for CronJob compatibility
 * 
 * Deployment:
 * - Runs as CronJob in OpenShift
 * - Secrets mounted by Vault sidecar/init container
 * - No direct Vault API calls
 */
@SpringBootApplication
@EnableScheduling
public class OrderStatusBatchApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(OrderStatusBatchApplication.class, args);
    }
}

// Made with Bob
