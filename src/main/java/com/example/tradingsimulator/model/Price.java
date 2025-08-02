package com.example.tradingsimulator.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Getter
@Setter
@Table(name="price")
public class Price {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String ticker;
    @Column(precision = 19, scale = 4)
    private BigDecimal price;

    public Price() {}

    public Price(@NotBlank String ticker , BigDecimal price) {
        this.ticker = ticker;
        this.price = price;
    }

}
