package com.example.tradingsimulator.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "user_transactions")
public class UserTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long orderId;
    private Long userId;
    private String ticker;
    private BigDecimal quantity;

    @Enumerated(EnumType.STRING)
    private OrderType orderType;

    private String idempotencyKey;
    private Instant timestamp;
    private String status;

    public UserTransaction(Long orderId, Long userId, String ticker,
                           BigDecimal quantity, OrderType orderType,
                           String idempotencyKey, Instant timestamp) {
        this.orderId = orderId;
        this.userId = userId;
        this.ticker = ticker;
        this.quantity = quantity;
        this.orderType = orderType;
        this.idempotencyKey = idempotencyKey;
        this.timestamp = timestamp;
        this.status = "PENDING";
    }
}