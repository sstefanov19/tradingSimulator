package com.example.tradingsimulator.dto;

import com.example.tradingsimulator.model.OrderType;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
public class OrderDto {
    private Long orderId;
    private String userId;
    private String ticker;
    private BigDecimal quantity;
    private OrderType orderType;
    private String executionType;
    private String status;
    private Instant timestamp;

    public OrderDto(){}

    public OrderDto(Long orderId,
                    String userId,
                    String ticker,
                    BigDecimal quantity,
                    OrderType orderType,
                    String executionType,
                    String status,
                    Instant timestamp) {
        this.orderId = orderId;
        this.userId = userId;
        this.ticker = ticker;
        this.quantity = quantity;
        this.orderType = orderType;
        this.executionType = executionType;
        this.status = status;
        this.timestamp = timestamp;
    }

}
