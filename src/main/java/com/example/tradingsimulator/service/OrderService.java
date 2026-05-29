package com.example.tradingsimulator.service;

import com.example.tradingsimulator.dto.OrderRequestDto;
import com.example.tradingsimulator.exception.InsufficientFundsException;
import com.example.tradingsimulator.exception.InsufficientHoldingsException;
import com.example.tradingsimulator.kafka.OutboxPendingEvent;
import com.example.tradingsimulator.model.Holding;
import com.example.tradingsimulator.model.Idempotency;
import com.example.tradingsimulator.model.Order;
import com.example.tradingsimulator.model.User;
import com.example.tradingsimulator.model.UserTransaction;
import com.example.tradingsimulator.repository.HoldingRepository;
import com.example.tradingsimulator.repository.OrderRepository;
import com.example.tradingsimulator.repository.UserRepository;
import com.example.tradingsimulator.repository.UserTransactionRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final UserTransactionRepository userTransactionRepository;
    private final IdempotencyService idempotencyService;
    private final ApplicationEventPublisher eventPublisher;
    private final PriceTickerService priceService;
    private final UserRepository userRepository;
    private final HoldingRepository holdingRepository;

    public OrderService(OrderRepository orderRepository,
                        UserTransactionRepository userTransactionRepository,
                        IdempotencyService idempotencyService,
                        ApplicationEventPublisher eventPublisher,
                        PriceTickerService priceService,
                        UserRepository userRepository,
                        HoldingRepository holdingRepository) {
        this.orderRepository = orderRepository;
        this.userTransactionRepository = userTransactionRepository;
        this.idempotencyService = idempotencyService;
        this.eventPublisher = eventPublisher;
        this.priceService = priceService;
        this.userRepository = userRepository;
        this.holdingRepository = holdingRepository;
    }

    @Transactional
    public Long publishOrder(OrderRequestDto request, String idempotencyKey) {
        Optional<Idempotency> existing = idempotencyService.findById(idempotencyKey);
        if (existing.isPresent()) {
            return existing.get().getOrderId();
        }

        BigDecimal reservedAmount = reserve(request);

        Order pending = new Order(
                request.userId(), request.ticker(), request.quantity(),
                BigDecimal.ZERO, request.orderType(), "MARKET", "PENDING", Instant.now()
        );
        pending.setReservedAmount(reservedAmount);
        Order saved = orderRepository.save(pending);

        userTransactionRepository.save(new UserTransaction(
                saved.getId(), request.userId(), request.ticker(),
                request.quantity(), request.orderType(), idempotencyKey, saved.getTimestamp()
        ));

        eventPublisher.publishEvent(new OutboxPendingEvent());

        return saved.getId();
    }

    // Enforce the affordability invariant at admission: reserve cash for BUYs and holding
    // quantity for SELLs so the system never accepts an order it cannot honor. Returns the
    // cash reserved for the order (null for SELL, which reserves quantity instead).
    private BigDecimal reserve(OrderRequestDto request) {
        return switch (request.orderType()) {
            case BUY -> reserveFunds(request);
            case SELL -> {
                reserveHolding(request);
                yield null;
            }
        };
    }

    private BigDecimal reserveFunds(OrderRequestDto request) {
        BigDecimal cost = priceService.getPrice(request.ticker()).price()
                .multiply(request.quantity());

        User user = userRepository.findByIdWithLock(request.userId())
                .orElseThrow(() -> new IllegalArgumentException("User not found!"));

        BigDecimal available = user.getBalance().subtract(user.getReserved());
        if (available.compareTo(cost) < 0) {
            throw new InsufficientFundsException(
                    "Insufficient balance to place order: need " + cost + ", available " + available);
        }

        user.setReserved(user.getReserved().add(cost));
        userRepository.save(user);
        return cost;
    }

    private void reserveHolding(OrderRequestDto request) {
        Holding holding = holdingRepository
                .findByUserIdAndTicker(request.userId(), request.ticker())
                .orElseThrow(() -> new InsufficientHoldingsException(
                        "No holding for " + request.ticker() + " to sell"));

        BigDecimal available = holding.getQuantity().subtract(holding.getReservedQuantity());
        if (available.compareTo(request.quantity()) < 0) {
            throw new InsufficientHoldingsException(
                    "Insufficient quantity to sell: need " + request.quantity() + ", available " + available);
        }

        holding.setReservedQuantity(holding.getReservedQuantity().add(request.quantity()));
        holdingRepository.save(holding);
    }
}
