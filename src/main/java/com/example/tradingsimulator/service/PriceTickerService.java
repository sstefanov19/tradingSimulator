package com.example.tradingsimulator.service;

import com.example.tradingsimulator.dto.PriceDto;
import org.springframework.stereotype.Service;


@Service
public class PriceTickerService implements PriceService {


    private final AlphaVantageClient alphaVantageClient;

    public PriceTickerService(AlphaVantageClient alphaVantageClient) {
        this.alphaVantageClient = alphaVantageClient;
    }

    @Override
    public PriceDto getPrice(String ticker) {
        return alphaVantageClient.getPriceForTicker(ticker);
    }
}
