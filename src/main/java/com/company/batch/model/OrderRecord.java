package com.company.batch.model;

/**
 * DTO representing an order record from the input file
 * 
 * This is a simple POJO used to transfer data between batch components.
 */
public class OrderRecord {
    
    private String orderId;
    private String status;
    
    public OrderRecord() {
    }
    
    public OrderRecord(String orderId, String status) {
        this.orderId = orderId;
        this.status = status;
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
    
    @Override
    public String toString() {
        return "OrderRecord{" +
                "orderId='" + orderId + '\'' +
                ", status='" + status + '\'' +
                '}';
    }
}

// Made with Bob
