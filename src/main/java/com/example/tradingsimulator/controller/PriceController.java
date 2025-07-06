package com.example.tradingsimulator.controller;


import com.example.tradingsimulator.dto.PriceDto;
import com.example.tradingsimulator.service.PriceTickerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class PriceController {


    private final PriceTickerService priceTickerService;

    public PriceController(PriceTickerService priceTickerService) {
        this.priceTickerService = priceTickerService;
    }

    @GetMapping("/prices/{ticker}")
    public ResponseEntity<PriceDto> getPrice(@PathVariable String ticker) {

       PriceDto price = priceTickerService.getPrice(ticker);
        return  ResponseEntity.ok(price);
    }

}
