package com.example.tradingsimulator.service;

import com.example.tradingsimulator.dto.PriceDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@Service
public class AlphaVantageClient {

    private final RestTemplate restTemplate;
    private final String apiKey;
    private final String baseUrl;

    public AlphaVantageClient(
            RestTemplate restTemplate,
            @Value("${alphavantage.api.key}") String apiKey,
            @Value("${alphavantage.base.url}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
    }

    public PriceDto getPriceForTicker(String ticker) {
        String url = String.format("%s?function=GLOBAL_QUOTE&symbol=%s&apikey=%s",
                baseUrl, ticker, apiKey);

        Map<String, Object> response = restTemplate.getForObject(url, Map.class);

        if (response == null) {
            throw new RuntimeException("No data found for ticker: " + ticker);
        }

        Map<String, String> globalQuote = (Map<String, String>) response.get("Global Quote");
        if (globalQuote == null || globalQuote.isEmpty()) {
            throw new RuntimeException("No data found for ticker: " + ticker);
        }

        String priceStr = globalQuote.get("05. price");
        BigDecimal price = new BigDecimal(priceStr);

        return new PriceDto(
                ticker,
                price,
                Instant.now()
        );
    }
}