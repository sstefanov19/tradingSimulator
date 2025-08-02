package com.example.tradingsimulator.controller;


import com.example.tradingsimulator.dto.PriceDto;
import com.example.tradingsimulator.model.Price;
import com.example.tradingsimulator.service.PriceTickerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1")
public class PriceController {


    private final PriceTickerService priceTickerService;

    public PriceController(PriceTickerService priceTickerService) {
        this.priceTickerService = priceTickerService;
    }

    @GetMapping("/prices/{ticker}")
    public ResponseEntity<Price> getPrice(@PathVariable String ticker) {

       Price price = priceTickerService.getPriceSaved(ticker);
        return  ResponseEntity.ok(price);
    }

    @PostMapping("/prices/{ticker}")
    public ResponseEntity<PriceDto> savePrice(@PathVariable String ticker) {

        PriceDto savedPrice = priceTickerService.savePrice(ticker);

        return ResponseEntity.ok(savedPrice);
    }

}
