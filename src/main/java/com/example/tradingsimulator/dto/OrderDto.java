package com.example.tradingsimulator.dto;

import com.example.tradingsimulator.model.OrderType;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderDto(
        Long orderId,
        Long userId,
        String ticker,
        BigDecimal quantity,
        OrderType orderType,
        String executionType,
        String status,
        Instant timestamp
) {}
