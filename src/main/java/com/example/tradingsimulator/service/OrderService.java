package com.example.tradingsimulator.service;

import com.example.tradingsimulator.dto.OrderRequestDto;
import com.example.tradingsimulator.kafka.OutboxPendingEvent;
import com.example.tradingsimulator.model.Idempotency;
import com.example.tradingsimulator.model.Order;
import com.example.tradingsimulator.model.UserTransaction;
import com.example.tradingsimulator.repository.OrderRepository;
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

    public OrderService(OrderRepository orderRepository,
                        UserTransactionRepository userTransactionRepository,
                        IdempotencyService idempotencyService,
                        ApplicationEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.userTransactionRepository = userTransactionRepository;
        this.idempotencyService = idempotencyService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Long publishOrder(OrderRequestDto request, String idempotencyKey) {
        Optional<Idempotency> existing = idempotencyService.findById(idempotencyKey);
        if (existing.isPresent()) {
            return existing.get().getOrderId();
        }

        Order pending = new Order(
                request.userId(), request.ticker(), request.quantity(),
                BigDecimal.ZERO, request.orderType(), "MARKET", "PENDING", Instant.now()
        );
        Order saved = orderRepository.save(pending);

        userTransactionRepository.save(new UserTransaction(
                saved.getId(), request.userId(), request.ticker(),
                request.quantity(), request.orderType(), idempotencyKey, saved.getTimestamp()
        ));

        eventPublisher.publishEvent(new OutboxPendingEvent());

        return saved.getId();
    }
}