package com.example.tradingsimulator;

import com.example.tradingsimulator.kafka.DltListener;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The DLT listener exists so dead-lettered orders are observable, not silently parked.
 * The WHY: without the metric, "landed in order.DLT" is just a different flavour of
 * silent drop — nothing tells an operator it happened.
 */
class DltListenerTest {

    @Test
    void onDead_incrementsDltCounter() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        DltListener listener = new DltListener(registry);
        ConsumerRecord<String, Object> record = new ConsumerRecord<>("order.DLT", 0, 0L, "idem-1", null);

        listener.onDead(record, "non-retryable boom");

        assertEquals(1.0, registry.get("orders.dlt").counter().count());
    }
}
