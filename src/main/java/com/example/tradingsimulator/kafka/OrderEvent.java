package com.example.tradingsimulator.kafka;

import com.example.tradingsimulator.model.OrderType;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderEvent(
        Long orderId,
        Long userId,
        String ticker,
        BigDecimal quantity,
        OrderType orderType,
        String idempotencyKey,
        Instant timestamp
) {}