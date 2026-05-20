package com.example.tradingsimulator.service;

import com.example.tradingsimulator.dto.PriceDto;
import com.example.tradingsimulator.kafka.OrderEvent;
import com.example.tradingsimulator.model.Holding;
import com.example.tradingsimulator.model.Order;
import com.example.tradingsimulator.repository.HoldingRepository;
import com.example.tradingsimulator.repository.OrderRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Slf4j
@Service
public class OrderExecutionService {

    private final OrderRepository orderRepository;
    private final HoldingRepository holdingRepository;
    private final PriceTickerService priceService;
    private final UserService userService;
    private final IdempotencyService idempotencyService;

    private final Counter buyCounter;
    private final Counter sellCounter;
    private final Counter failedCounter;
    private final Counter duplicateCounter;
    private final Timer executionTimer;

    public OrderExecutionService(OrderRepository orderRepository,
                                 UserService userService,
                                 HoldingRepository holdingRepository,
                                 PriceTickerService priceService,
                                 IdempotencyService idempotencyService,
                                 MeterRegistry registry) {
        this.orderRepository = orderRepository;
        this.userService = userService;
        this.holdingRepository = holdingRepository;
        this.priceService = priceService;
        this.idempotencyService = idempotencyService;

        this.buyCounter = Counter.builder("orders.executed").tag("type", "BUY").register(registry);
        this.sellCounter = Counter.builder("orders.executed").tag("type", "SELL").register(registry);
        this.failedCounter = Counter.builder("orders.failed").register(registry);
        this.duplicateCounter = Counter.builder("orders.duplicate").register(registry);
        this.executionTimer = Timer.builder("orders.execution.duration").register(registry);
    }

    @Transactional
    public void executeOrder(OrderEvent event) {
        if (idempotencyService.findById(event.idempotencyKey()).isPresent()) {
            duplicateCounter.increment();
            return;
        }

        Order order = orderRepository.findById(event.orderId()).orElse(null);
        if (order == null) {
            log.warn("Order {} not found, skipping stale event", event.orderId());
            return;
        }

        executionTimer.record(() -> {
            try {
                PriceDto priceDto = priceService.getPrice(event.ticker());
                BigDecimal totalCost = priceDto.price().multiply(event.quantity());

                switch (event.orderType()) {
                    case BUY -> {
                        buyOrder(event, totalCost);
                        buyCounter.increment();
                    }
                    case SELL -> {
                        sellOrder(event, totalCost);
                        sellCounter.increment();
                    }
                }

                order.setTotalCost(totalCost);
                order.setStatus("FILLED");
                orderRepository.save(order);

                idempotencyService.save(event.idempotencyKey(), event.orderId());
            } catch (Exception e) {
                failedCounter.increment();
                order.setStatus("FAILED");
                orderRepository.save(order);
                throw new RuntimeException(e);
            }
        });
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