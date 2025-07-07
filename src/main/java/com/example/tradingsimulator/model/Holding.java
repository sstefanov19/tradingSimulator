package com.example.tradingsimulator.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name= "holdings")
@Getter
@Setter
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
}
