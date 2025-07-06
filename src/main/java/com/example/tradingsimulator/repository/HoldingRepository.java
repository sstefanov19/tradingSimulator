package com.example.tradingsimulator.repository;

import com.example.tradingsimulator.model.Holding;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface HoldingRepository extends JpaRepository<Holding, Long> {

    Optional<Holding> findByUserIdAndTicker(Long userId, String ticker);

    List<Holding> findByUserId(Long userId);
}
