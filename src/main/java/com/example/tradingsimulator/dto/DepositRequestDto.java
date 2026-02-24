package com.example.tradingsimulator.dto;

import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record DepositRequestDto(
        Long userId,
        @Positive(message = "Amount cannot be negative")  BigDecimal amount
) {}
