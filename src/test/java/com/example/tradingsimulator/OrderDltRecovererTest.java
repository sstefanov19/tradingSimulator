package com.example.tradingsimulator;

import com.example.tradingsimulator.kafka.OrderDltRecoverer;
import com.example.tradingsimulator.kafka.OrderEvent;
import com.example.tradingsimulator.model.OrderType;
import com.example.tradingsimulator.service.OrderExecutionService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;

import java.math.BigDecimal;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * The recoverer is the terminal step for a dead-lettered order. The WHY behind these
 * tests: a DLT'd order is terminal, so its admission-time escrow must be released
 * exactly like a business rejection — otherwise every poison message permanently
 * locks the user's funds. A deserialization failure has no parseable order, so there
 * is nothing to release and the raw bytes go to the DLT for manual inspection.
 */
@ExtendWith(MockitoExtension.class)
class OrderDltRecovererTest {

    @Mock private DeadLetterPublishingRecoverer publisher;
    @Mock private OrderExecutionService executionService;

    private OrderDltRecoverer recoverer;

    @BeforeEach
    void setUp() {
        recoverer = new OrderDltRecoverer(publisher, executionService);
    }

    private OrderEvent event() {
        return new OrderEvent(1L, 2L, "AAPL", new BigDecimal("3"), OrderType.BUY, "idem-1", Instant.now());
    }

    @Test
    void deadLetteringParseableOrder_releasesEscrowBeforePublishing() {
        OrderEvent ev = event();
        ConsumerRecord<String, Object> record = new ConsumerRecord<>("order", 0, 0L, "idem-1", ev);
        RuntimeException cause = new RuntimeException("boom");

        recoverer.accept(record, cause);

        // Escrow must be released, and only after that should the record leave for the DLT —
        // so a crash mid-recovery never publishes a record whose reservation is still held.
        InOrder order = inOrder(executionService, publisher);
        order.verify(executionService).rejectAndRelease(ev);
        order.verify(publisher).accept(record, cause);
    }

    @Test
    void deadLetteringDeserializationFailure_publishesWithoutReleasingEscrow() {
        // ErrorHandlingDeserializer surfaces a malformed payload as a null value.
        ConsumerRecord<String, Object> record = new ConsumerRecord<>("order", 0, 0L, "idem-1", null);
        RuntimeException cause = new RuntimeException("deserialization failed");

        recoverer.accept(record, cause);

        verify(executionService, never()).rejectAndRelease(any());
        verify(publisher).accept(record, cause);
    }
}
