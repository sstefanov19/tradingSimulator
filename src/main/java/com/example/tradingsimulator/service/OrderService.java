package com.example.tradingsimulator.service;

import com.example.tradingsimulator.dto.OrderDto;
import com.example.tradingsimulator.model.Order;
import com.example.tradingsimulator.model.OrderType;
import com.example.tradingsimulator.repository.OrderRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final UserService userService;

    public OrderService(OrderRepository orderRepository, UserService userService) {
        this.orderRepository = orderRepository;
        this.userService = userService;
    }

    public OrderDto placeOrder(String userId,
                               String ticker,
                               OrderType orderType,
                               BigDecimal executedPrice,
                               BigDecimal totalCost) {

        if(orderType.equals(OrderType.BUY)){
            BigDecimal userBalance = userService.getBalance(userId);
            if(totalCost.compareTo(userBalance) <= 0){
                throw new RuntimeException("Insufficient balance!");
            }
            userService.decreaseBalance(userId, totalCost.subtract(executedPrice));
        }

        if(orderType.equals(OrderType.SELL)){
            BigDecimal userBalance = userService.getBalance(userId);

            userService.increaseBalance(userId, userBalance.add(executedPrice));
        }

        Order order = new Order(
                userId,
                ticker,
                executedPrice,
                totalCost,
                orderType,
                "MARKET",
                "FILLED",
                Instant.now()
        );

        Order savedOrder = orderRepository.save(order);

        return new OrderDto(
                savedOrder.getId(),
                savedOrder.getUserId(),
                savedOrder.getTicker(),
                savedOrder.getExecutedPrice(),
                savedOrder.getOrderType(),
                savedOrder.getExecutionType(),
                savedOrder.getStatus(),
                savedOrder.getTimestamp()
        );
    }
}
