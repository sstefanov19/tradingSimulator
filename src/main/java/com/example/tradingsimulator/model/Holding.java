package com.example.tradingsimulator.model;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name= "holdings")
public class Holding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    private String ticker;

    private BigDecimal quantity;

    public Holding() {}

    public Holding(Long userId, String ticker, BigDecimal quantity) {
        this.userId = userId;
        this.ticker = ticker;
        this.quantity = quantity;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getTicker() {
        return ticker;
    }

    public void setTicker(String ticker) {
        this.ticker = ticker;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }
}
