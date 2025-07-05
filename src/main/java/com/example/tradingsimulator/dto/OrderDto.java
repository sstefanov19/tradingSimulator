package com.example.tradingsimulator.dto;

import com.example.tradingsimulator.model.OrderType;

import java.math.BigDecimal;
import java.time.Instant;

public class OrderDto {
    private Long orderId;
    private String userId;
    private String ticker;
    private BigDecimal quantity;
    private BigDecimal executedPrice;
    private OrderType orderType;
    private String executionType;
    private String status;
    private Instant timestamp;

    public OrderDto(){}

    public OrderDto(Long orderId,
                    String userId,
                    String ticker,
//                    BigDecimal quantity,
                    BigDecimal executedPrice,
                    OrderType orderType,
                    String executionType,
                    String status,
                    Instant timestamp) {
        this.orderId = orderId;
        this.userId = userId;
        this.ticker = ticker;
//        this.quantity = quantity;
        this.executedPrice = executedPrice;
        this.orderType = orderType;
        this.executionType = executionType;
        this.status = status;
        this.timestamp = timestamp;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getTicker() {
        return ticker;
    }

    public void setTicker(String ticker) {
        this.ticker = ticker;
    }
//
//    public BigDecimal getQuantity() {
//        return quantity;
//    }
//
//    public void setQuantity(BigDecimal quantity) {
//        this.quantity = quantity;
//    }

    public BigDecimal getExecutedPrice() {
        return executedPrice;
    }

    public void setExecutedPrice(BigDecimal executedPrice) {
        this.executedPrice = executedPrice;
    }

    public OrderType getOrderType() {
        return orderType;
    }

    public void setOrderType(OrderType orderType) {
        this.orderType = orderType;
    }

    public String getExecutionType() {
        return executionType;
    }

    public void setExecutionType(String executionType) {
        this.executionType = executionType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
}
