package com.example.tradingsimulator.kafka;

import com.example.tradingsimulator.model.UserTransaction;
import com.example.tradingsimulator.repository.UserTransactionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

@Slf4j
@Component
public class OutboxPoller {

    private final UserTransactionRepository userTransactionRepository;
    private final KafkaTemplate<String, OrderEvent> kafkaTemplate;

    public OutboxPoller(UserTransactionRepository userTransactionRepository,
                        KafkaTemplate<String, OrderEvent> kafkaTemplate) {
        this.userTransactionRepository = userTransactionRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOutboxPending(OutboxPendingEvent event) {
        poll();
    }

    @Scheduled(fixedDelay = 30_000)
    public void pollFallback() {
        poll();
    }

    public void poll() {
        List<UserTransaction> pending = userTransactionRepository.findByStatus("PENDING");
        for (UserTransaction tx : pending) {
            try {
                OrderEvent event = new OrderEvent(
                        tx.getOrderId(), tx.getUserId(), tx.getTicker(),
                        tx.getQuantity(), tx.getOrderType(), tx.getIdempotencyKey(), tx.getTimestamp()
                );
                kafkaTemplate.send("order", tx.getIdempotencyKey(), event).get();
                tx.setStatus("PUBLISHED");
                userTransactionRepository.save(tx);
            } catch (Exception e) {
                log.error("Failed to publish outbox entry {}: {}", tx.getId(), e.getMessage());
            }
        }
    }
}