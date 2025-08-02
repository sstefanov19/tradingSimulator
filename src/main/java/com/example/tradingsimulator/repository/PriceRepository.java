package com.example.tradingsimulator.repository;


import com.example.tradingsimulator.model.Price;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PriceRepository  extends JpaRepository<Price, Long> {

    Optional<Price> findByTicker(String ticker);

    boolean existsByTicker(String ticker);

}
