package com.example.tradingsimulator.dto;

import com.example.tradingsimulator.model.OrderType;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class OrderRequestDto {
    private String userId;
    private String ticker;
    private BigDecimal quantity;
    private OrderType orderType;
}
