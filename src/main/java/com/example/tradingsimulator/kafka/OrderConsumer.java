package com.example.tradingsimulator.kafka;

import com.example.tradingsimulator.service.OrderExecutionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class OrderConsumer {

    private final OrderExecutionService orderExecutionService;

    public OrderConsumer(OrderExecutionService orderExecutionService) {
        this.orderExecutionService = orderExecutionService;
    }

    @KafkaListener(topics = "order", groupId = "order-service")
    public void consume(OrderEvent event) {
        log.info("OrderConsumer processing order: {}", event.orderId());
        orderExecutionService.executeOrder(event);
    }
}