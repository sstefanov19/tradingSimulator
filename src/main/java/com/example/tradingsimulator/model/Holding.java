package com.example.tradingsimulator.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;

@Entity
@Table(name = "holdings")
@Getter
@Setter
public class Holding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    private String username;

    private String ticker;

    private BigDecimal quantity;

    @ColumnDefault("0")
    @Column(nullable = false)
    private BigDecimal reservedQuantity = BigDecimal.ZERO;

    public Holding() {
    }

    public Holding(Long userId, String username, String ticker, BigDecimal quantity) {
        this.userId = userId;
        this.username = username;
        this.ticker = ticker;
        this.quantity = quantity;
    }
}
