package com.example.tradingsimulator.repository;

import com.example.tradingsimulator.model.Holding;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;

public interface HoldingRepository extends JpaRepository<Holding, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Holding> findByUserIdAndTicker(Long userId, String ticker);

    Optional<List<Holding>> findByUsername(String username);

}
