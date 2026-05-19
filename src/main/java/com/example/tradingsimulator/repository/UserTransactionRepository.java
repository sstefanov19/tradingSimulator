package com.example.tradingsimulator.repository;

import com.example.tradingsimulator.model.UserTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserTransactionRepository extends JpaRepository<UserTransaction, Long> {
    List<UserTransaction> findByStatus(String status);
}