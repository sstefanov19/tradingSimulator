package com.example.tradingsimulator.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic orderTopic() {
        return TopicBuilder.name("order").partitions(3).replicas(1).build();
    }

    // Mirror the source topic's partition count: DeadLetterPublishingRecoverer's
    // default resolver publishes a failed record to the SAME partition number on
    // the DLT, so order.DLT must have at least as many partitions as "order".
    @Bean
    public NewTopic orderDltTopic() {
        return TopicBuilder.name("order.DLT").partitions(3).replicas(1).build();
    }
}