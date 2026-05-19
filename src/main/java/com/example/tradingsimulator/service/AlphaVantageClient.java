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

        if (response != null) {
            String rateLimitNote = (String) response.getOrDefault("Note", response.get("Information"));
            if (rateLimitNote != null) {
                log.warn("AlphaVantage rate limit hit for ticker {}: {}", ticker, rateLimitNote);
                throw new RuntimeException("AlphaVantage rate limit exceeded for ticker: " + ticker);
            }
            Map<String, String> globalQuote = (Map<String, String>) response.get("Global Quote");
            if (globalQuote != null && !globalQuote.isEmpty()) {
                String priceStr = globalQuote.get("05. price");
                return new PriceDto(ticker, new BigDecimal(priceStr));
            }
        }

        // Fall back to Binance for crypto tickers (no API key needed, 1200 req/min)
        String binanceSymbol = ticker.toUpperCase() + "USDT";
        String binanceUrl = "https://api.binance.com/api/v3/ticker/price?symbol=" + binanceSymbol;

        Map<String, Object> binanceResponse = restTemplate.getForObject(binanceUrl, Map.class);

        if (binanceResponse == null || !binanceResponse.containsKey("price")) {
            log.warn("No Binance data for ticker {}, response: {}", ticker, binanceResponse);
            throw new RuntimeException("No data found for ticker: " + ticker);
        }

        String priceStr = (String) binanceResponse.get("price");
        return new PriceDto(ticker, new BigDecimal(priceStr));
    }
}