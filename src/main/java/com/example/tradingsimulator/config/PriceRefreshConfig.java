package com.example.tradingsimulator.config;

import com.example.tradingsimulator.dto.PriceDto;
import com.example.tradingsimulator.service.AlphaVantageClient;
import com.example.tradingsimulator.service.TickerTracker;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PriceRefreshConfig {
    private final AlphaVantageClient alphaVantageClient;
    private final RedisTemplate<String , String> redisTemplate;
    private final TickerTracker tickerTracker;

    public PriceRefreshConfig(AlphaVantageClient alphaVantageClient, RedisTemplate<String, String> redisTemplate, TickerTracker tickerTracker) {
        this.alphaVantageClient = alphaVantageClient;
        this.redisTemplate = redisTemplate;
        this.tickerTracker = tickerTracker;
    }

    @Scheduled(fixedRate = 30000)
    public void refreshCachedPrice() {
        for(String ticker : tickerTracker.getRequestedTickers()) {
            try{
                PriceDto price = alphaVantageClient.getPriceForTicker(ticker);
                if(price != null && price.price() != null) {
                    redisTemplate.opsForValue().set("CACHE_PRICE::" +ticker , price.price().toString());
                }
            }catch (Exception e) {
                throw new RuntimeException(e.getMessage());
            }
        }
    }
}
