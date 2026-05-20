package com.example.tradingsimulator.service;

import com.example.tradingsimulator.dto.PriceDto;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;


@Service
public class PriceTickerService {

    private final BinanceClient binanceClient;
    private final TickerTracker tickerTracker;
    private final MeterRegistry registry;

    public PriceTickerService(BinanceClient binanceClient, TickerTracker tickerTracker, MeterRegistry registry) {
        this.binanceClient = binanceClient;
        this.tickerTracker = tickerTracker;
        this.registry = registry;
    }

    @Cacheable(value = "CACHE_PRICE", key = "#ticker")
    public PriceDto getPrice(String ticker) {
        tickerTracker.markRequest(ticker);

        Timer.Sample sample = Timer.start(registry);
        try {
            PriceDto priceDto = binanceClient.getPriceForTicker(ticker);
            if (priceDto == null || priceDto.price() == null) {
                registry.counter("price.lookup.errors", "ticker", ticker).increment();
                throw new RuntimeException("No data found for ticker: " + ticker);
            }
            registry.counter("price.lookups", "ticker", ticker).increment();
            return priceDto;
        } finally {
            sample.stop(Timer.builder("price.lookup.duration").tag("ticker", ticker).register(registry));
        }
    }
}
