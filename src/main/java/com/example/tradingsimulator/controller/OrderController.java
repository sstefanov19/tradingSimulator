package com.example.tradingsimulator.controller;

import com.example.tradingsimulator.dto.OrderDto;
import com.example.tradingsimulator.dto.OrderRequestDto;
import com.example.tradingsimulator.service.OrderService;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/orders")
    public ResponseEntity<OrderDto> placeOrder(@Valid  @RequestBody OrderRequestDto request) {
        OrderDto order = orderService.placeOrder(request);
        return ResponseEntity.ok(order);
    }
}
