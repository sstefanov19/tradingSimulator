package com.example.tradingsimulator.service;

import com.example.tradingsimulator.model.Idempotency;
import com.example.tradingsimulator.repository.IdempotencyRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class IdempotencyService {

    private final IdempotencyRepository idempotencyRepository;

    public IdempotencyService(IdempotencyRepository idempotencyRepository) {
        this.idempotencyRepository = idempotencyRepository;
    }

    public Optional<Idempotency> findById(String key) {
        return idempotencyRepository.findById(key);
    }

    public void save(String key, Long orderId) {
        idempotencyRepository.save(new Idempotency(key, orderId));
    }
}
