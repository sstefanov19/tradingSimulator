package com.example.tradingsimulator.service;

import com.example.tradingsimulator.dto.OrderDto;
import com.example.tradingsimulator.model.Holding;
import com.example.tradingsimulator.model.Order;
import com.example.tradingsimulator.model.OrderType;
import com.example.tradingsimulator.repository.HoldingRepository;
import com.example.tradingsimulator.repository.OrderRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final HoldingRepository holdingRepository;
    private final UserService userService;

    public OrderService(OrderRepository orderRepository,
                        UserService userService ,
                        HoldingRepository holdingRepository) {
        this.orderRepository = orderRepository;
        this.userService = userService;
        this.holdingRepository = holdingRepository;
    }

    public OrderDto placeOrder(String userId,
                               String ticker,
                               BigDecimal quantity,
                               OrderType orderType,
                               BigDecimal totalCost) {

        if(orderType.equals(OrderType.BUY)){
            BigDecimal userBalance = userService.getBalance(userId);

            if (userBalance.compareTo(totalCost) < 0) {
                throw new RuntimeException("Insufficient balance!");
            }

            userService.decreaseBalance(userId, totalCost);

            Holding holding = holdingRepository
                    .findByUserIdAndTicker(Long.parseLong(userId), ticker)
                    .orElse(new Holding(Long.parseLong(userId), ticker, BigDecimal.ZERO));

            holding.setQuantity(holding.getQuantity().add(quantity));
            holdingRepository.save(holding);
        }


        if(orderType.equals(OrderType.SELL)){
            userService.increaseBalance(userId, totalCost);

            Holding holding = holdingRepository
                    .findByUserIdAndTicker(Long.parseLong(userId), ticker)
                    .orElse(new Holding(Long.parseLong(userId), ticker, BigDecimal.ZERO));

            holding.setQuantity(holding.getQuantity().subtract(quantity));
            holdingRepository.save(holding);
        }

        Order order = new Order(
                userId,
                ticker,
                quantity,
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
                savedOrder.getQuantity(),
                savedOrder.getOrderType(),
                savedOrder.getExecutionType(),
                savedOrder.getStatus(),
                savedOrder.getTimestamp()
        );
    }
}
