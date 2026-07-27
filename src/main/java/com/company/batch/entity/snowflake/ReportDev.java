package com.company.batch.entity.snowflake;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Snowflake Entity - ReportDev
 * 
 * Represents order status records in Snowflake warehouse.
 * Only orders with changed status are persisted to this table.
 * 
 * Table structure should exist in Snowflake:
 * CREATE TABLE REPORT_DEV (
 *   ID NUMBER AUTOINCREMENT PRIMARY KEY,
 *   ORDER_ID VARCHAR(50) NOT NULL,
 *   ORDER_STATUS VARCHAR(20) NOT NULL,
 *   PREVIOUS_STATUS VARCHAR(20),
 *   UPDATED_AT TIMESTAMP_NTZ,
 *   PROCESSED_AT TIMESTAMP_NTZ DEFAULT CURRENT_TIMESTAMP()
 * );
 */
@Entity
@Table(name = "REPORT_DEV", schema = "MY_SCM")
public class ReportDev {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;
    
    @Column(name = "ORDER_ID", nullable = false, length = 50)
    private String orderId;
    
    @Column(name = "ORDER_STATUS", nullable = false, length = 20)
    private String orderStatus;
    
    @Column(name = "PREVIOUS_STATUS", length = 20)
    private String previousStatus;
    
    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;
    
    @Column(name = "PROCESSED_AT")
    private LocalDateTime processedAt;
    
    // Constructors
    public ReportDev() {
        this.processedAt = LocalDateTime.now();
    }
    
    public ReportDev(String orderId, String orderStatus, String previousStatus) {
        this.orderId = orderId;
        this.orderStatus = orderStatus;
        this.previousStatus = previousStatus;
        this.updatedAt = LocalDateTime.now();
        this.processedAt = LocalDateTime.now();
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getOrderId() {
        return orderId;
    }
    
    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }
    
    public String getOrderStatus() {
        return orderStatus;
    }
    
    public void setOrderStatus(String orderStatus) {
        this.orderStatus = orderStatus;
    }
    
    public String getPreviousStatus() {
        return previousStatus;
    }
    
    public void setPreviousStatus(String previousStatus) {
        this.previousStatus = previousStatus;
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    public LocalDateTime getProcessedAt() {
        return processedAt;
    }
    
    public void setProcessedAt(LocalDateTime processedAt) {
        this.processedAt = processedAt;
    }
    
    @Override
    public String toString() {
        return "ReportDev{" +
                "id=" + id +
                ", orderId='" + orderId + '\'' +
                ", orderStatus='" + orderStatus + '\'' +
                ", previousStatus='" + previousStatus + '\'' +
                ", updatedAt=" + updatedAt +
                ", processedAt=" + processedAt +
                '}';
    }
}

// Made with Bob
