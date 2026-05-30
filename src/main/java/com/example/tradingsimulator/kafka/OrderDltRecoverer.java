
package com.example.tradingsimulator.kafka;

import com.example.tradingsimulator.service.OrderExecutionService;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.stereotype.Component;

/**
 * Terminal handler for an order message that has exhausted its retries.
 *
 * <pre>
 *   record exhausted retries
 *           │
 *           ▼
 *   value instanceof OrderEvent ?
 *     ├── yes ─▶ executionService.rejectAndRelease(event)   // REJECTED + escrow released
 *     └── no  ─▶ (deserialization failure: null value, raw  // nothing to release
 *                 bytes only — manual inspection/replay)
 *           │
 *           ▼
 *   DeadLetterPublishingRecoverer.accept ─▶ order.DLT
 * </pre>
 *
 * A dead-lettered order is terminal, so it must release its admission-time reservation
 * exactly like a business rejection — otherwise every poison message leaks escrow.
 */
@Slf4j
@Component
public class OrderDltRecoverer implements ConsumerRecordRecoverer {

    private final DeadLetterPublishingRecoverer publisher;
    private final OrderExecutionService executionService;

    public OrderDltRecoverer(DeadLetterPublishingRecoverer publisher,
                             OrderExecutionService executionService) {
        this.publisher = publisher;
        this.executionService = executionService;
    }

    @Override
    public void accept(ConsumerRecord<?, ?> record, Exception exception) {
        if (record.value() instanceof OrderEvent event) {
            log.warn("Dead-lettering order {}, releasing reservation", event.orderId());
            executionService.rejectAndRelease(event);
        } else {
            log.error("Dead-lettering unparseable record at {}-{}@{}: {}",
                    record.topic(), record.partition(), record.offset(),
                    exception.getMessage());
        }
        publisher.accept(record, exception);
    }
}
