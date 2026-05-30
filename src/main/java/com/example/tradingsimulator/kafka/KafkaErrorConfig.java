package com.example.tradingsimulator.kafka;

import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.Serializer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DelegatingByTypeSerializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * Dead-letter + bounded-retry wiring for the order pipeline.
 *
 * <pre>
 *   order topic ─▶ orderKafkaListenerContainerFactory (THIS factory only)
 *                    └─ DefaultErrorHandler
 *                         ├─ FixedBackOff(2s × 2)  in-thread retries
 *                         └─ OrderDltRecoverer ─▶ rejectAndRelease + order.DLT
 * </pre>
 *
 * The error handler is attached to a dedicated container factory rather than exposed
 * as a global {@code CommonErrorHandler} bean. Spring Boot would otherwise apply a
 * lone {@code CommonErrorHandler} to <em>every</em> listener — including
 * {@link PriceConsumer}, which shares the {@code order} topic. A failed price snapshot
 * must never reject the order or release its escrow.
 */
@Configuration
public class KafkaErrorConfig {

    private static final String DLT_TOPIC = "order.DLT";

    /**
     * Template used only by the recoverer to republish to the DLT. A
     * {@link DelegatingByTypeSerializer} routes by value type so it can serialize both
     * a normal {@link OrderEvent} (retries-exhausted case) and the original {@code byte[]}
     * payload (deserialization-failure case) without mangling the latter into base64 JSON.
     */
    @Bean
    public KafkaTemplate<Object, Object> dltKafkaTemplate(KafkaProperties properties,
                                                          ObjectProvider<SslBundles> sslBundles) {
        Map<String, Object> producerProps = properties.buildProducerProperties(sslBundles.getIfAvailable());

        Map<Class<?>, Serializer<?>> delegates = new HashMap<>();
        delegates.put(byte[].class, new ByteArraySerializer());
        delegates.put(OrderEvent.class, new JsonSerializer<>());

        DefaultKafkaProducerFactory<Object, Object> factory = new DefaultKafkaProducerFactory<>(producerProps);
        factory.setValueSerializer(new DelegatingByTypeSerializer(delegates));
        return new KafkaTemplate<>(factory);
    }

    /** Publishes failed records to order.DLT on the same partition number as the source. */
    @Bean
    public DeadLetterPublishingRecoverer deadLetterPublishingRecoverer(KafkaTemplate<Object, Object> dltKafkaTemplate) {
        return new DeadLetterPublishingRecoverer(dltKafkaTemplate,
                (record, exception) -> new TopicPartition(DLT_TOPIC, record.partition()));
    }

    /**
     * Dedicated factory for {@link OrderConsumer}. The {@link DefaultErrorHandler} is built
     * inline (not a bean) so Boot does not auto-apply it to the default factory used by
     * {@link PriceConsumer} and {@code DltListener}.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> orderKafkaListenerContainerFactory(
            ConsumerFactory<String, Object> consumerFactory,
            OrderDltRecoverer orderDltRecoverer) {

        DefaultErrorHandler errorHandler =
                new DefaultErrorHandler(orderDltRecoverer, new FixedBackOff(2000L, 2L));

        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }
}
