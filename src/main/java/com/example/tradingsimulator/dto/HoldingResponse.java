package com.example.tradingsimulator.dto;

import java.math.BigDecimal;

public record HoldingResponse(
        Long id,
        Long userId,
        String ticker,
        BigDecimal quantity) {
}
