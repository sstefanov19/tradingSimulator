package com.example.tradingsimulator.repository;

import com.example.tradingsimulator.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Integer> {

    List<Order> findByUserId(String userId);

    List<Order> findByTicker(String ticker);

    List<Order> findByUserIdAndTicker(String userId, String ticker);
}
