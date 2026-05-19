package com.example.tradingsimulator.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.example.tradingsimulator.dto.HoldingResponse;
import com.example.tradingsimulator.model.Holding;
import com.example.tradingsimulator.repository.HoldingRepository;

@Service
public class HoldingService {

    private final HoldingRepository holdingRepository;

    public HoldingService(HoldingRepository holdingRepository) {
        this.holdingRepository = holdingRepository;
    }

    public List<HoldingResponse> getHoldingsByUsername(String username) {

        List<Holding> holdings = holdingRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("This user doenst have any holdings"));

        return holdings.stream()
                .map(this::mapTResponse)
                .toList();
    }

    public HoldingResponse mapTResponse(Holding holding) {
        return new HoldingResponse(
                holding.getId(),
                holding.getUserId(),
                holding.getTicker(),
                holding.getQuantity());
    }

}
