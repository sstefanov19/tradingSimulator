package com.example.tradingsimulator.model;

import jakarta.persistence.*;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String userId;

    private String ticker;

    private BigDecimal quantity;

    private BigDecimal totalCost;

    @Enumerated(EnumType.STRING)
    private OrderType orderType;

    private String executionType;

    private String status;

    private Instant timestamp;


    public Order() {}

    public Order(String userId,
                 String ticker,
                 BigDecimal quantity,
                 BigDecimal totalCost,
                 OrderType orderType,
                 String executionType,
                 String status,
                 Instant timestamp) {
        this.userId = userId;
        this.ticker = ticker;
        this.quantity = quantity;
        this.totalCost = totalCost;
        this.orderType = orderType;
        this.executionType = executionType;
        this.status = status;
        this.timestamp = timestamp;
    }
}
