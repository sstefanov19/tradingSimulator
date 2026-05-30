package com.example.tradingsimulator;

import com.example.tradingsimulator.kafka.OrderDltRecoverer;
import com.example.tradingsimulator.kafka.OrderEvent;
import com.example.tradingsimulator.model.OrderType;
import com.example.tradingsimulator.service.OrderExecutionService;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.core.log.LogAccessor;
import org.springframework.kafka.support.serializer.DelegatingByTypeSerializer;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.support.serializer.SerializationUtils;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.condition.EmbeddedKafkaCondition;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.util.backoff.FixedBackOff;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/**
 * End-to-end verification of the DLQ wiring against a real (embedded) broker — the
 * retry loop, ErrorHandlingDeserializer routing, and DLT publish can only be proven
 * with a live broker, not mocks.
 *
 * <pre>
 *   produce ─▶ order ─▶ container (ErrorHandlingDeserializer)
 *                          └─ DefaultErrorHandler(FixedBackOff)
 *                               └─ OrderDltRecoverer ─▶ rejectAndRelease + order.DLT
 * </pre>
 *
 * The error handler is built here with a 0ms backoff (vs production's 2s) — this test
 * proves the routing/recovery logic, not the wall-clock interval, which is a config value.
 */
@EmbeddedKafka(partitions = 3, topics = {"order", "order.DLT"})
class OrderDlqIntegrationTest {

    private static final LogAccessor LOG = new LogAccessor(OrderDlqIntegrationTest.class);

    private EmbeddedKafkaBroker broker;

    @BeforeEach
    void setUp() {
        broker = EmbeddedKafkaCondition.getBroker();
    }

    @Test
    void exhaustedRetries_routeToDltAndReleaseEscrow() throws Exception {
        OrderExecutionService executionService = mock(OrderExecutionService.class);
        KafkaMessageListenerContainer<String, OrderEvent> container = startContainer(
                record -> { throw new RuntimeException("always fails"); },
                executionService);
        Consumer<byte[], byte[]> dlt = dltConsumerAtEnd();
        try {
            OrderEvent event = new OrderEvent(1L, 2L, "AAPL", new BigDecimal("3"),
                    OrderType.BUY, "idem-exhaust", Instant.now());
            eventTemplate().send("order", "idem-exhaust", event).get(10, TimeUnit.SECONDS);

            ConsumerRecord<byte[], byte[]> dead =
                    KafkaTestUtils.getSingleRecord(dlt, "order.DLT", Duration.ofSeconds(20));
            assertNotNull(dead, "exhausted message should land on order.DLT");
            // The terminal order must have released its escrow before being dead-lettered.
            verify(executionService, timeout(5000))
                    .rejectAndRelease(argThat(e -> e.orderId().equals(1L)));
        } finally {
            container.stop();
            dlt.close();
        }
    }

    @Test
    void deserializationFailure_routesToDltWithoutReleasingEscrow() throws Exception {
        OrderExecutionService executionService = mock(OrderExecutionService.class);
        KafkaMessageListenerContainer<String, OrderEvent> container = startContainer(
                record -> { /* never reached: a malformed payload fails before the listener */ },
                executionService);
        Consumer<byte[], byte[]> dlt = dltConsumerAtEnd();
        try {
            Map<String, Object> props = KafkaTestUtils.producerProps(broker);
            KafkaTemplate<String, String> rawTemplate = new KafkaTemplate<>(
                    new DefaultKafkaProducerFactory<>(props, new StringSerializer(), new StringSerializer()));
            rawTemplate.send("order", "idem-bad", "this-is-not-valid-json").get(10, TimeUnit.SECONDS);

            ConsumerRecord<byte[], byte[]> dead =
                    KafkaTestUtils.getSingleRecord(dlt, "order.DLT", Duration.ofSeconds(20));
            assertNotNull(dead, "unparseable message should land on order.DLT, not loop");
            // No parseable order means no reservation to release.
            verify(executionService, never()).rejectAndRelease(any());
        } finally {
            container.stop();
            dlt.close();
        }
    }

    @Test
    void transientFailureThenSuccess_fillsWithoutDeadLettering() throws Exception {
        OrderExecutionService executionService = mock(OrderExecutionService.class);
        AtomicInteger attempts = new AtomicInteger();
        CountDownLatch succeeded = new CountDownLatch(1);
        KafkaMessageListenerContainer<String, OrderEvent> container = startContainer(record -> {
            if (attempts.incrementAndGet() == 1) {
                throw new RuntimeException("transient blip");
            }
            succeeded.countDown();
        }, executionService);
        Consumer<byte[], byte[]> dlt = dltConsumerAtEnd();
        try {
            OrderEvent event = new OrderEvent(2L, 2L, "AAPL", new BigDecimal("1"),
                    OrderType.BUY, "idem-transient", Instant.now());
            eventTemplate().send("order", "idem-transient", event).get(10, TimeUnit.SECONDS);

            assertTrue(succeeded.await(20, TimeUnit.SECONDS), "retry should eventually succeed");
            assertEquals(2, attempts.get(), "exactly one retry then success");

            ConsumerRecords<byte[], byte[]> deadLetters = KafkaTestUtils.getRecords(dlt, Duration.ofSeconds(3));
            assertTrue(deadLetters.isEmpty(), "a recovered transient failure must not be dead-lettered");
            verify(executionService, never()).rejectAndRelease(any());
        } finally {
            container.stop();
            dlt.close();
        }
    }

    // --- helpers -----------------------------------------------------------------

    /** Builds a container on "order" wired exactly like production: ErrorHandlingDeserializer
     *  + DefaultErrorHandler + OrderDltRecoverer, with a fast backoff for the test. */
    private KafkaMessageListenerContainer<String, OrderEvent> startContainer(
            MessageListener<String, OrderEvent> listener, OrderExecutionService executionService) {

        JsonDeserializer<OrderEvent> json = new JsonDeserializer<>(OrderEvent.class);
        json.addTrustedPackages("*");
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps("order-service-it", "false", broker);
        DefaultKafkaConsumerFactory<String, OrderEvent> consumerFactory = new DefaultKafkaConsumerFactory<>(
                consumerProps,
                new ErrorHandlingDeserializer<>(new StringDeserializer()),
                new ErrorHandlingDeserializer<>(json));

        // Replicate what the production @KafkaListener method adapter does: a record whose
        // value failed deserialization carries the exception in a header — rethrow it so the
        // error handler sees a (non-retryable) DeserializationException, just like in prod.
        MessageListener<String, OrderEvent> guarded = record -> {
            DeserializationException de = SerializationUtils.getExceptionFromHeader(
                    record, SerializationUtils.VALUE_DESERIALIZER_EXCEPTION_HEADER, LOG);
            if (de != null) {
                throw de;
            }
            listener.onMessage(record);
        };

        ContainerProperties containerProps = new ContainerProperties("order");
        containerProps.setMessageListener(guarded);

        KafkaMessageListenerContainer<String, OrderEvent> container =
                new KafkaMessageListenerContainer<>(consumerFactory, containerProps);
        container.setCommonErrorHandler(buildErrorHandler(executionService));
        container.start();
        ContainerTestUtils.waitForAssignment(container, 3);
        return container;
    }

    private DefaultErrorHandler buildErrorHandler(OrderExecutionService executionService) {
        Map<String, Object> producerProps = KafkaTestUtils.producerProps(broker);
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        Map<Class<?>, Serializer<?>> delegates = new HashMap<>();
        delegates.put(byte[].class, new ByteArraySerializer());
        delegates.put(OrderEvent.class, new JsonSerializer<>());
        DefaultKafkaProducerFactory<Object, Object> dltFactory = new DefaultKafkaProducerFactory<>(producerProps);
        dltFactory.setValueSerializer(new DelegatingByTypeSerializer(delegates));

        DeadLetterPublishingRecoverer publisher = new DeadLetterPublishingRecoverer(
                new KafkaTemplate<>(dltFactory),
                (record, exception) -> new TopicPartition("order.DLT", record.partition()));
        // Make the DLT publish synchronous so a send failure surfaces in the test deterministically.
        publisher.setFailIfSendResultIsError(true);
        publisher.setWaitForSendResultTimeout(Duration.ofSeconds(10));
        OrderDltRecoverer recoverer = new OrderDltRecoverer(publisher, executionService);
        // 0ms backoff, 2 retries (3 attempts total) — fast routing check; production uses 2s.
        return new DefaultErrorHandler(recoverer, new FixedBackOff(0L, 2L));
    }

    private KafkaTemplate<String, OrderEvent> eventTemplate() {
        Map<String, Object> props = KafkaTestUtils.producerProps(broker);
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(
                props, new StringSerializer(), new JsonSerializer<>()));
    }

    /** A DLT consumer positioned at the end of the topic, so each test only sees the
     *  records it produces (the embedded broker is shared across the class). */
    private Consumer<byte[], byte[]> dltConsumerAtEnd() {
        Map<String, Object> props = KafkaTestUtils.consumerProps("dlt-it-" + System.nanoTime(), "true", broker);
        Consumer<byte[], byte[]> consumer = new DefaultKafkaConsumerFactory<>(
                props, new ByteArrayDeserializer(), new ByteArrayDeserializer()).createConsumer();
        broker.consumeFromAnEmbeddedTopic(consumer, "order.DLT");
        consumer.seekToEnd(consumer.assignment());
        // seekToEnd is lazy — force it to resolve NOW (before the test produces) by reading each
        // position, otherwise "end" resolves on the first poll AFTER the record lands and skips it.
        consumer.assignment().forEach(consumer::position);
        return consumer;
    }
}
