package com.example.tradingsimulator.dto;

import com.example.tradingsimulator.model.OrderType;

import java.math.BigDecimal;

public record OrderRequestDto(
        Long userId,
        String ticker,
        BigDecimal quantity,
        OrderType orderType
) {}
