package com.example.tradingsimulator.controller;

import com.example.tradingsimulator.model.Holding;
import com.example.tradingsimulator.repository.HoldingRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class HoldingController {

    private HoldingRepository holdingRepository;

    public HoldingController(HoldingRepository holdingRepository) {
        this.holdingRepository = holdingRepository;
    }


    @GetMapping("/holdings/{userId}")
    public ResponseEntity<List<Holding>> getHoldings(@PathVariable String userId) {
        List<Holding> holdings = holdingRepository.findByUserId(Long.parseLong(userId));

        return ResponseEntity.ok(holdings);
    }
}
