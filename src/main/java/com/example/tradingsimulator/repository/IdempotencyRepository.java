package com.example.tradingsimulator.repository;

import com.example.tradingsimulator.model.Idempotency;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyRepository extends JpaRepository<Idempotency, String> {
}
