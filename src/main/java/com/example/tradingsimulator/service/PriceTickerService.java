package com.example.tradingsimulator.service;

import com.example.tradingsimulator.dto.PriceDto;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;


@Service
public class PriceTickerService {


    private final AlphaVantageClient alphaVantageClient;
    private final TickerTracker tickerTracker;

    public PriceTickerService(AlphaVantageClient alphaVantageClient , TickerTracker tickerTracker) {
        this.alphaVantageClient = alphaVantageClient;
        this.tickerTracker  = tickerTracker;
    }


    @Cacheable(value = "CACHE_PRICE" , key = "#ticker")
    public PriceDto getPrice(String ticker) {
        tickerTracker.markRequest(ticker);
        PriceDto priceDto = alphaVantageClient.getPriceForTicker(ticker);
        if (priceDto == null || priceDto.price() == null) {
            throw new RuntimeException("No data found for ticker: " + ticker);
        }
        return priceDto;
    }
}
