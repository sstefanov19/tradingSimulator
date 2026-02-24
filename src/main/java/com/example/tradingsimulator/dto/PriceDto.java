package com.example.tradingsimulator.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record PriceDto(
        @NotBlank String ticker,
        @NotNull  BigDecimal price
) {}
