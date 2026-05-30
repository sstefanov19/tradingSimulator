package com.example.tradingsimulator.service;

import com.example.tradingsimulator.dto.PriceDto;
import com.example.tradingsimulator.exception.InsufficientFundsException;
import com.example.tradingsimulator.exception.InsufficientHoldingsException;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;

@Slf4j
@Service
public class OrderExecutionService {

    private final OrderRepository orderRepository;
    private final HoldingRepository holdingRepository;
    private final PriceTickerService priceService;
    private final UserService userService;
    private final IdempotencyService idempotencyService;
    private final TransactionTemplate transactionTemplate;

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
                                 PlatformTransactionManager transactionManager,
                                 MeterRegistry registry) {
        this.orderRepository = orderRepository;
        this.userService = userService;
        this.holdingRepository = holdingRepository;
        this.priceService = priceService;
        this.idempotencyService = idempotencyService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);

        this.buyCounter = Counter.builder("orders.executed").tag("type", "BUY").register(registry);
        this.sellCounter = Counter.builder("orders.executed").tag("type", "SELL").register(registry);
        this.failedCounter = Counter.builder("orders.failed").register(registry);
        this.duplicateCounter = Counter.builder("orders.duplicate").register(registry);
        this.executionTimer = Timer.builder("orders.execution.duration").register(registry);
    }

    public void executeOrder(OrderEvent event) {
        if (idempotencyService.findById(event.idempotencyKey()).isPresent()) {
            duplicateCounter.increment();
            return;
        }

        if (orderRepository.findById(event.orderId()).isEmpty()) {
            log.warn("Order {} not found, skipping stale event", event.orderId());
            return;
        }

        try {
            // Settle in its own transaction so a failure rolls back cleanly, leaving the
            // admission-time reservation intact for the reject/retry decision below.
            executionTimer.record(() -> transactionTemplate.executeWithoutResult(s -> doExecute(event)));
        } catch (InsufficientFundsException | InsufficientHoldingsException e) {
            // Business rejection — terminal. Release the reservation, mark REJECTED in a
            // committed transaction, and swallow so the Kafka offset advances.
            failedCounter.increment();
            log.warn("Order {} rejected: {}", event.orderId(), e.getMessage());
            rejectAndRelease(event);
        } catch (Exception e) {
            // Transient/unknown failure — leave the reservation intact and rethrow so the
            // message is retried. Nothing was committed, so retry re-executes cleanly.
            failedCounter.increment();
            log.error("Order {} failed transiently, will retry: {}", event.orderId(), e.getMessage());
            throw e;
        }
    }

    private void doExecute(OrderEvent event) {
        Order order = orderRepository.findById(event.orderId()).orElseThrow();

        PriceDto priceDto = priceService.getPrice(event.ticker());
        BigDecimal totalCost = priceDto.price().multiply(event.quantity());

        switch (event.orderType()) {
            case BUY -> {
                buyOrder(event, order, totalCost);
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
    }

    private void buyOrder(OrderEvent event, Order order, BigDecimal totalCost) {
        userService.settleBuy(event.userId(), totalCost, order.getReservedAmount());

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
            throw new InsufficientHoldingsException("Insufficient quantity!");
        }

        userService.increaseBalance(event.userId(), totalCost);

        holding.setQuantity(holding.getQuantity().subtract(event.quantity()));
        holding.setReservedQuantity(holding.getReservedQuantity().subtract(event.quantity()));
        if (holding.getQuantity().compareTo(BigDecimal.ZERO) == 0
                && holding.getReservedQuantity().compareTo(BigDecimal.ZERO) == 0) {
            holdingRepository.delete(holding);
        } else {
            holdingRepository.save(holding);
        }
    }

    // Release the admission-time reservation and mark the order REJECTED. Runs in its own
    // committed transaction so the outcome survives the rolled-back execution transaction.
    // Public because the Kafka error handler also calls this when a message is dead-lettered
    // (a DLT'd order is terminal, so it must release escrow exactly like a business rejection).
    public void rejectAndRelease(OrderEvent event) {
        transactionTemplate.executeWithoutResult(s -> {
            Order order = orderRepository.findById(event.orderId()).orElse(null);
            if (order == null) return;

            switch (event.orderType()) {
                case BUY -> {
                    if (order.getReservedAmount() != null) {
                        userService.releaseReservation(event.userId(), order.getReservedAmount());
                    }
                }
                case SELL -> holdingRepository
                        .findByUserIdAndTicker(event.userId(), event.ticker())
                        .ifPresent(h -> {
                            h.setReservedQuantity(h.getReservedQuantity().subtract(event.quantity()));
                            holdingRepository.save(h);
                        });
            }

            order.setStatus("REJECTED");
            orderRepository.save(order);
        });
    }
}
