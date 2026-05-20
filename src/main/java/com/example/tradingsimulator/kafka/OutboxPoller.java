package com.example.tradingsimulator.kafka;

import com.example.tradingsimulator.model.UserTransaction;
import com.example.tradingsimulator.repository.UserTransactionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
public class OutboxPoller {

    private static final int BATCH_SIZE = 100;
    private static final int STUCK_THRESHOLD_MINUTES = 5;

    private final UserTransactionRepository userTransactionRepository;
    private final KafkaTemplate<String, OrderEvent> kafkaTemplate;
    private final TransactionTemplate transactionTemplate;

    public OutboxPoller(UserTransactionRepository userTransactionRepository,
                        KafkaTemplate<String, OrderEvent> kafkaTemplate,
                        PlatformTransactionManager transactionManager) {
        this.userTransactionRepository = userTransactionRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOutboxPending(OutboxPendingEvent event) {
        poll();
    }

    @Scheduled(fixedDelay = 30_000)
    public void pollFallback() {
        Instant threshold = Instant.now().minusSeconds(60L * STUCK_THRESHOLD_MINUTES);
        Integer reset = transactionTemplate.execute(s ->
                userTransactionRepository.resetStuck(threshold));
        if (reset != null && reset > 0) {
            log.warn("Reaped {} stuck PROCESSING rows older than {} minutes", reset, STUCK_THRESHOLD_MINUTES);
        }
        poll();
    }

    public void poll() {
        // Phase 1: claim rows atomically — SKIP LOCKED prevents multi-node races,
        // PROCESSING status prevents re-claiming before Phase 2 completes.
        // Transaction commits here, releasing the DB lock before any Kafka I/O.
        List<UserTransaction> claimed = transactionTemplate.execute(status -> {
            List<UserTransaction> rows = userTransactionRepository.findPendingForUpdate(BATCH_SIZE);
            Instant now = Instant.now();
            rows.forEach(tx -> { tx.setStatus("PROCESSING"); tx.setClaimedAt(now); });
            return userTransactionRepository.saveAll(rows);
        });

        if (claimed == null || claimed.isEmpty()) return;

        // Phase 2: publish outside the transaction — no DB lock held during network I/O.
        for (UserTransaction tx : claimed) {
            try {
                kafkaTemplate.send("order", tx.getIdempotencyKey(), new OrderEvent(
                        tx.getOrderId(), tx.getUserId(), tx.getTicker(),
                        tx.getQuantity(), tx.getOrderType(), tx.getIdempotencyKey(), tx.getTimestamp()
                )).get();
                tx.setStatus("PUBLISHED");
            } catch (Exception e) {
                log.error("Failed to publish outbox entry {}: {}", tx.getId(), e.getMessage());
                tx.setStatus("PENDING");
            }
            userTransactionRepository.save(tx);
        }
    }
}