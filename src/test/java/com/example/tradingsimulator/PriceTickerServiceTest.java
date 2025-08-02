package com.example.tradingsimulator;

import com.example.tradingsimulator.dto.PriceDto;
import com.example.tradingsimulator.service.AlphaVantageClient;
import com.example.tradingsimulator.service.PriceTickerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@AutoConfigureMockMvc
public class PriceTickerServiceTest {

    @Container
    @ServiceConnection
    static GenericContainer redis = new GenericContainer(DockerImageName.parse("redis:7.4.2"))
            .withExposedPorts(6379);

    @BeforeEach
    void setUp() {
        reset(alphaVantageClient);
        cacheManager.getCache("PRICE_CACHE").clear();
    }

    @MockitoBean
    private AlphaVantageClient alphaVantageClient;

    @Autowired
    private PriceTickerService priceTickerService;

    @Autowired
    private CacheManager cacheManager;

    @Test
    void getPrice_shouldCacheResult() {
        String ticker = "AAPL";
        PriceDto mockedPrice = new PriceDto(ticker, new BigDecimal("123.45"));
        Cache cache = cacheManager.getCache("PRICE_CACHE");

        when(alphaVantageClient.getPriceForTicker(ticker)).thenReturn(mockedPrice);

        PriceDto firstCall = priceTickerService.getPrice(ticker);
        assertEquals(mockedPrice.getPrice(), firstCall.getPrice());
        verify(alphaVantageClient, times(1)).getPriceForTicker(ticker);

        // Verify the result is now cached
        Cache.ValueWrapper cachedValue = cache.get(ticker);
        assertNotNull(cachedValue);
        PriceDto cachedPrice = (PriceDto) cachedValue.get();
        assertEquals(mockedPrice.getPrice(), cachedPrice.getPrice());

        // Second call - should use cached result, no additional service call
        PriceDto secondCall = priceTickerService.getPrice(ticker);
        assertEquals(mockedPrice.getPrice(), secondCall.getPrice());
        verify(alphaVantageClient, times(1)).getPriceForTicker(ticker); // Still only 1 call

        // Third call - should still use cached result
        PriceDto thirdCall = priceTickerService.getPrice(ticker);
        assertEquals(mockedPrice.getPrice(), thirdCall.getPrice());
        verify(alphaVantageClient, times(1)).getPriceForTicker(ticker); // Still only 1 call
    }

    @Test
    void getPrice_shouldCacheDifferentTickersSeparately() {
        String ticker1 = "AAPL";
        String ticker2 = "GOOGL";
        PriceDto applePrice = new PriceDto(ticker1, new BigDecimal("123.45"));
        PriceDto googlePrice = new PriceDto(ticker2, new BigDecimal("2500.00"));

        when(alphaVantageClient.getPriceForTicker(ticker1)).thenReturn(applePrice);
        when(alphaVantageClient.getPriceForTicker(ticker2)).thenReturn(googlePrice);

        // Call for AAPL
        priceTickerService.getPrice(ticker1);
        priceTickerService.getPrice(ticker1); // Second call should use cache

        // Call for GOOGL
        priceTickerService.getPrice(ticker2);
        priceTickerService.getPrice(ticker2); // Second call should use cache

        // Verify each service was called only once per ticker
        verify(alphaVantageClient, times(1)).getPriceForTicker(ticker1);
        verify(alphaVantageClient, times(1)).getPriceForTicker(ticker2);
    }

    @Test
    void getPrice_shouldUseServiceAfterCacheEviction() {
        String ticker = "AAPL";
        PriceDto mockedPrice = new PriceDto(ticker, new BigDecimal("123.45"));
        Cache cache = cacheManager.getCache("PRICE_CACHE");

        when(alphaVantageClient.getPriceForTicker(ticker)).thenReturn(mockedPrice);

        // First call - hits service
        priceTickerService.getPrice(ticker);
        verify(alphaVantageClient, times(1)).getPriceForTicker(ticker);

        cache.evict(ticker);
        assertNull(cache.get(ticker));

        // Next call should hit service again
        priceTickerService.getPrice(ticker);
        verify(alphaVantageClient, times(2)).getPriceForTicker(ticker);
    }
}