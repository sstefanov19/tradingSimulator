package com.example.tradingsimulator.dto;

import com.example.tradingsimulator.model.OrderType;

import java.math.BigDecimal;

public class OrderRequestDto {
    private String userId;
    private String ticker;
    private BigDecimal quantity;
    private OrderType orderType;


    public String getUserId() {
        return userId;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {this.quantity = quantity;}

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getTicker() {
        return ticker;
    }

    public void setTicker(String ticker) {
        this.ticker = ticker;
    }

    public OrderType getOrderType() {
        return orderType;
    }

    public void setOrderType(OrderType orderType) {
        this.orderType = orderType;
    }
}
