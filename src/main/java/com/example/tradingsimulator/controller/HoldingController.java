package com.example.tradingsimulator.controller;

import com.example.tradingsimulator.dto.HoldingResponse;
import com.example.tradingsimulator.service.HoldingService;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class HoldingController {

    private final HoldingService holdingService;

    public HoldingController(HoldingService holdingService) {
        this.holdingService = holdingService;
    }

    @GetMapping("/holdings/me")
    public ResponseEntity<List<HoldingResponse>> getHoldings(Authentication authentication) {
        String username = authentication.getName();
        List<HoldingResponse> holdings = holdingService.getHoldingsByUsername(username);

        return ResponseEntity.ok(holdings);
    }
}
