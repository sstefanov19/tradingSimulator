package com.example.tradingsimulator.service;

import com.example.tradingsimulator.dto.PriceDto;
import com.example.tradingsimulator.model.Price;
import com.example.tradingsimulator.repository.PriceRepository;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;


@Service
public class PriceTickerService implements PriceService {


    private final AlphaVantageClient alphaVantageClient;
    private final PriceRepository priceRepository;

    public PriceTickerService(AlphaVantageClient alphaVantageClient , PriceRepository priceRepository) {
        this.alphaVantageClient = alphaVantageClient;
        this.priceRepository = priceRepository;
    }


    @CachePut(value = "CACHE_PRICE" , key = "#ticker")
    public PriceDto savePrice(String ticker) {

        if(priceRepository.existsByTicker(ticker)) {
            throw new RuntimeException("Ticker " + ticker + " already exists!");
        }

        PriceDto priceDto = getPrice(ticker);
        if (priceDto == null || priceDto.getPrice() == null) {
            throw new RuntimeException("No data found for ticker: " + ticker);
        }

        Price priceEntity = new Price(priceDto.getTicker() , priceDto.getPrice());
        priceRepository.save(priceEntity);

        return priceDto;
    }

    @Cacheable(value = "CACHE_PRICE" , key = "#ticker")
    public Price getPriceSaved(String ticker) {

        return priceRepository.findByTicker(ticker)
                .orElseThrow(() -> new RuntimeException("Wasn't able to find price for" + ticker));
    }

    @Override
    public PriceDto getPrice(String ticker) {
        PriceDto priceDto = alphaVantageClient.getPriceForTicker(ticker);
        if (priceDto == null || priceDto.getPrice() == null) {
            throw new RuntimeException("No data found for ticker: " + ticker);
        }
        return priceDto;
    }
}
