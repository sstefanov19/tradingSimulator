package com.example.tradingsimulator.controller;

import com.example.tradingsimulator.dto.OrderDto;
import com.example.tradingsimulator.dto.OrderRequestDto;
import com.example.tradingsimulator.service.OrderService;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;



@RestController
@RequestMapping("/api/v1")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/orders")
    public ResponseEntity<OrderDto> placeOrder(
            @Valid @RequestBody OrderRequestDto request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        OrderDto order = orderService.placeOrder(request, idempotencyKey);
        return ResponseEntity.ok(order);
    }
}
