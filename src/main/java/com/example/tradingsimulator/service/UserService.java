package com.example.tradingsimulator.service;

import com.example.tradingsimulator.model.User;
import com.example.tradingsimulator.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class UserService{

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public BigDecimal getBalance(String userId) {
        User user = userRepository.findById(Long.parseLong(userId))
                .orElseThrow(() -> new IllegalArgumentException("User not found!"));

        return user.getBalance();
    }

    public void decreaseBalance(String userId , BigDecimal amount) {
        User user = userRepository.findById(Long.parseLong(userId))
                .orElseThrow(() -> new IllegalArgumentException("User not found!"));

        BigDecimal newBalance = user.getBalance().subtract(amount);
        if(newBalance.compareTo(BigDecimal.ZERO)< 0) {
            throw new RuntimeException("Insufficient balance!");
        }

        user.setBalance(newBalance);
        userRepository.save(user);
    }

    public BigDecimal increaseBalance(String userId, BigDecimal amount) {
        User user = userRepository.findById(Long.parseLong(userId))
                .orElseThrow(() -> new RuntimeException("User not found"));

        BigDecimal newBalance = user.getBalance().add(amount);
        user.setBalance(newBalance);
        userRepository.save(user);
        return newBalance;
    }

    public String findEmail(String userId) {
        User user = userRepository.findById(Long.parseLong(userId))
                .orElseThrow(() -> new RuntimeException("User not found!"));

        return user.getEmail();
    }
}
