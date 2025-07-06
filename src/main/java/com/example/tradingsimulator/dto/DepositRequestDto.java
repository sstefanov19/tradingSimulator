package com.example.tradingsimulator.dto;

import java.math.BigDecimal;

public class DepositRequestDto {

    private BigDecimal amount;

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }
}
