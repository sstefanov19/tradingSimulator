package com.example.tradingsimulator.controller;

import com.example.tradingsimulator.dto.OrderDto;
import com.example.tradingsimulator.dto.OrderRequestDto;
import com.example.tradingsimulator.dto.PriceDto;
import com.example.tradingsimulator.model.OrderType;
import com.example.tradingsimulator.service.OrderService;
import com.example.tradingsimulator.service.PriceService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class OrderController {

    private final PriceService priceService;
    private final OrderService orderService;

    public OrderController(PriceService priceService , OrderService orderService) {
        this.priceService = priceService;
        this.orderService = orderService;
    }

    @PostMapping("/orders")
    public ResponseEntity<OrderDto> placeOrder(@RequestBody OrderRequestDto request) {
        PriceDto priceDto = priceService.getPrice(request.getTicker());
        BigDecimal price = priceDto.getPrice();

        OrderDto order = orderService.placeOrder(
                request.getUserId(),
                request.getTicker(),
                request.getOrderType(),
                price,
                price
        );
        return ResponseEntity.ok(order);
    }
}
