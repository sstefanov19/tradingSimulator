package com.example.tradingsimulator.kafka;

import com.example.tradingsimulator.dto.PriceDto;
import com.example.tradingsimulator.model.PriceSnapshot;
import com.example.tradingsimulator.repository.PriceSnapshotRepository;
import com.example.tradingsimulator.service.PriceTickerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PriceConsumer {

    private final PriceTickerService priceTickerService;
    private final PriceSnapshotRepository priceSnapshotRepository;

    public PriceConsumer(PriceTickerService priceTickerService,
                         PriceSnapshotRepository priceSnapshotRepository) {
        this.priceTickerService = priceTickerService;
        this.priceSnapshotRepository = priceSnapshotRepository;
    }

    @KafkaListener(topics = "order", groupId = "price-service")
    public void consume(OrderEvent event) {
        log.info("PriceConsumer snapshotting price for order: {}", event.orderId());
        PriceDto priceDto = priceTickerService.getPrice(event.ticker());
        priceSnapshotRepository.save(
                new PriceSnapshot(event.orderId(), event.ticker(), priceDto.price(), event.timestamp())
        );
    }
}