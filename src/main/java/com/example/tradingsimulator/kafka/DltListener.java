package com.example.tradingsimulator.kafka;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * Surfaces dead-lettered orders so they are inspectable, not silently parked.
 * Logs the failure reason and increments the {@code orders.dlt} counter (scraped
 * by Prometheus alongside the existing order metrics).
 *
 * <p>Consumes via the default container factory, so it carries no retry/DLT error
 * handler of its own — it only logs and counts, and never rethrows, so a malformed
 * record (null value via ErrorHandlingDeserializer) cannot loop back into the DLT.
 */
@Slf4j
@Component
public class DltListener {

    private final Counter dltCounter;

    public DltListener(MeterRegistry registry) {
        this.dltCounter = Counter.builder("orders.dlt").register(registry);
    }

    @KafkaListener(topics = "order.DLT", groupId = "order-dlt")
    public void onDead(ConsumerRecord<String, Object> record,
                       @Header(name = KafkaHeaders.DLT_EXCEPTION_MESSAGE, required = false) String reason) {
        log.error("Order dead-lettered: key={} reason={}", record.key(), reason);
        dltCounter.increment();
    }
}
