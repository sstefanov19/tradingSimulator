package com.example.tradingsimulator.repository;

import com.example.tradingsimulator.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByUserId(Long userId);

    List<Order> findByTicker(String ticker);

    List<Order> findByUserIdAndTicker(Long userId, String ticker);
}
