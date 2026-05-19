package com.example.tradingsimulator.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "idempotency")
public class Idempotency {

    @Id
    private String id;
    
    private Long orderId;

    public Idempotency() {}

    public Idempotency(String id, Long orderId) {
        this.id = id;
        this.orderId = orderId;
    }

    public String getId() { return id; }
    public Long getOrderId() { return orderId; }
}
