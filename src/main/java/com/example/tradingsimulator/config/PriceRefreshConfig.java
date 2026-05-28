package com.example.tradingsimulator.config;

import com.example.tradingsimulator.dto.PriceDto;
import com.example.tradingsimulator.service.BinanceClient;
import com.example.tradingsimulator.service.TickerTracker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;

@Slf4j
@Component
public class PriceRefreshConfig {
    private final BinanceClient binanceClient;
    private final CacheManager cacheManager;
    private final TickerTracker tickerTracker;

    public PriceRefreshConfig(BinanceClient binanceClient, CacheManager cacheManager, TickerTracker tickerTracker) {
        this.binanceClient = binanceClient;
        this.cacheManager = cacheManager;
        this.tickerTracker = tickerTracker;
    }

    @Scheduled(fixedRate = 30000)
    public void refreshCachedPrice() {
        try (var exec = Executors.newVirtualThreadPerTaskExecutor()) {
            for (String ticker : tickerTracker.getRequestedTickers()) {
                exec.submit(() -> {
                    try {
                        PriceDto price = binanceClient.getPriceForTicker(ticker);
                        if (price != null && price.price() != null) {
                            var cache = cacheManager.getCache("CACHE_PRICE");
                            if (cache != null) cache.put(ticker, price);
                        }
                    } catch (Exception e) {
                        log.warn("Failed to refresh price for ticker {}: {}", ticker, e.getMessage());
                    }
                });
            }
        }
    }
}
