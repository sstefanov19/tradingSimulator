package com.example.tradingsimulator.kafka;

import com.example.tradingsimulator.dto.PriceDto;
import com.example.tradingsimulator.model.PriceSnapshot;
import com.example.tradingsimulator.repository.PriceSnapshotRepository;
import com.example.tradingsimulator.service.PriceTickerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

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
        try {
            PriceDto priceDto = priceTickerService.getPrice(event.ticker());
            priceSnapshotRepository.save(
                    new PriceSnapshot(event.orderId(), event.ticker(), priceDto.price(), event.timestamp())
            );
        } catch (HttpClientErrorException.BadRequest e) {
            log.warn("Invalid ticker {} for order {}, skipping snapshot", event.ticker(), event.orderId());
        }
    }
}