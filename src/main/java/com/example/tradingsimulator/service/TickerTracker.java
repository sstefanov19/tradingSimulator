package com.example.tradingsimulator.service;

import org.springframework.stereotype.Component;


import java.util.Collections;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TickerTracker {
    private final Set<String> requestedTickers = ConcurrentHashMap.newKeySet();

    public void markRequest(String ticker) {
        requestedTickers.add(ticker);
    }

    public Set<String> getRequestedTickers() {
        return Collections.unmodifiableSet(requestedTickers);
    }
}
