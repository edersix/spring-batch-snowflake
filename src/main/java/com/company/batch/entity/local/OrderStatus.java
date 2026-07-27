package com.company.batch.entity.local;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Local Entity - OrderStatus
 * 
 * Represents order status records in the local database.
 * Used for comparison against incoming file data to detect changes.
 * 
 * This entity is managed by the primary datasource.
 */
@Entity
@Table(name = "ORDER_STATUS")
public class OrderStatus {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "ORDER_ID", nullable = false, unique = true, length = 50)
    private String orderId;
    
    @Column(name = "STATUS", nullable = false, length = 20)
    private String status;
    
    @Column(name = "LAST_UPDATED")
    private LocalDateTime lastUpdated;
    
    // Constructors
    public OrderStatus() {
    }
    
    public OrderStatus(String orderId, String status) {
        this.orderId = orderId;
        this.status = status;
        this.lastUpdated = LocalDateTime.now();
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
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public LocalDateTime getLastUpdated() {
        return lastUpdated;
    }
    
    public void setLastUpdated(LocalDateTime lastUpdated) {
        this.lastUpdated = lastUpdated;
    }
    
    @Override
    public String toString() {
        return "OrderStatus{" +
                "id=" + id +
                ", orderId='" + orderId + '\'' +
                ", status='" + status + '\'' +
                ", lastUpdated=" + lastUpdated +
                '}';
    }
}

// Made with Bob
