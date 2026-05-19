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
@Table(name = "price_snapshots")
public class PriceSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long orderId;
    private String ticker;
    private BigDecimal price;
    private Instant timestamp;

    public PriceSnapshot(Long orderId, String ticker, BigDecimal price, Instant timestamp) {
        this.orderId = orderId;
        this.ticker = ticker;
        this.price = price;
        this.timestamp = timestamp;
    }
}