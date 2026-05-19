package com.example.tradingsimulator.service;

import com.example.tradingsimulator.dto.PriceDto;
import com.example.tradingsimulator.kafka.OrderEvent;
import com.example.tradingsimulator.model.Holding;
import com.example.tradingsimulator.model.Order;
import com.example.tradingsimulator.repository.HoldingRepository;
import com.example.tradingsimulator.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class OrderExecutionService {

    private final OrderRepository orderRepository;
    private final HoldingRepository holdingRepository;
    private final PriceTickerService priceService;
    private final UserService userService;
    private final IdempotencyService idempotencyService;

    public OrderExecutionService(OrderRepository orderRepository,
                                 UserService userService,
                                 HoldingRepository holdingRepository,
                                 PriceTickerService priceService,
                                 IdempotencyService idempotencyService) {
        this.orderRepository = orderRepository;
        this.userService = userService;
        this.holdingRepository = holdingRepository;
        this.priceService = priceService;
        this.idempotencyService = idempotencyService;
    }

    @Transactional
    public void executeOrder(OrderEvent event) {
        if (idempotencyService.findById(event.idempotencyKey()).isPresent()) {
            return;
        }

        Order order = orderRepository.findById(event.orderId())
                .orElseThrow(() -> new RuntimeException("Order not found: " + event.orderId()));

        try {
            PriceDto priceDto = priceService.getPrice(event.ticker());
            BigDecimal totalCost = priceDto.price().multiply(event.quantity());

            switch (event.orderType()) {
                case BUY -> buyOrder(event, totalCost);
                case SELL -> sellOrder(event, totalCost);
            }

            order.setTotalCost(totalCost);
            order.setStatus("FILLED");
            orderRepository.save(order);

            idempotencyService.save(event.idempotencyKey(), event.orderId());
        } catch (Exception e) {
            order.setStatus("FAILED");
            orderRepository.save(order);
            throw new RuntimeException(e);
        }
    }

    private void buyOrder(OrderEvent event, BigDecimal totalCost) {
        userService.decreaseBalance(event.userId(), totalCost);

        String username = userService.getUsername(event.userId());
        Holding holding = holdingRepository
                .findByUserIdAndTicker(event.userId(), event.ticker())
                .orElse(new Holding(event.userId(), username, event.ticker(), BigDecimal.ZERO));

        holding.setQuantity(holding.getQuantity().add(event.quantity()));
        holdingRepository.save(holding);
    }

    private void sellOrder(OrderEvent event, BigDecimal totalCost) {
        String username = userService.getUsername(event.userId());
        Holding holding = holdingRepository
                .findByUserIdAndTicker(event.userId(), event.ticker())
                .orElse(new Holding(event.userId(), username, event.ticker(), BigDecimal.ZERO));

        if (event.quantity().compareTo(holding.getQuantity()) > 0) {
            throw new RuntimeException("Insufficient quantity!");
        }

        userService.increaseBalance(event.userId(), totalCost);

        holding.setQuantity(holding.getQuantity().subtract(event.quantity()));
        if (holding.getQuantity().compareTo(BigDecimal.ZERO) == 0) {
            holdingRepository.delete(holding);
        } else {
            holdingRepository.save(holding);
        }
    }
}