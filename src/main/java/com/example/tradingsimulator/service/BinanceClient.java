package com.example.tradingsimulator.service;

import com.example.tradingsimulator.dto.PriceDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Map;

@Slf4j
@Service
public class BinanceClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public BinanceClient(
            RestTemplate restTemplate,
            @Value("${binance.base.url}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    public PriceDto getPriceForTicker(String ticker) {
        String symbol = ticker.toUpperCase() + "USDT";
        String url = String.format("%s/api/v3/ticker/price?symbol=%s", baseUrl, symbol);

        Map<String, Object> response = restTemplate.getForObject(url, Map.class);

        if (response == null || !response.containsKey("price")) {
            log.warn("No Binance data for ticker {}, response: {}", ticker, response);
            throw new RuntimeException("No data found for ticker: " + ticker);
        }

        String priceStr = (String) response.get("price");
        return new PriceDto(ticker, new BigDecimal(priceStr));
    }
}
