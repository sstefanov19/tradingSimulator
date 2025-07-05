package com.example.tradingsimulator.service;

import com.example.tradingsimulator.dto.PriceDto;

public interface PriceService {

    PriceDto getPrice(String ticker);
}
