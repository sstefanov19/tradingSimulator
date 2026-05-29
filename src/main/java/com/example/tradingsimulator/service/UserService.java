package com.example.tradingsimulator.service;

import com.example.tradingsimulator.exception.InsufficientFundsException;
import com.example.tradingsimulator.model.User;
import com.example.tradingsimulator.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public void decreaseBalance(Long userId, BigDecimal amount) {
        User user = userRepository.findByIdWithLock(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found!"));
        BigDecimal newBalance = user.getBalance().subtract(amount);
        if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new RuntimeException("Insufficient balance!");
        }

        user.setBalance(newBalance);
        userRepository.save(user);
    }

    // Settle a filled BUY: debit the actual cost and free the cash reserved at admission.
    public void settleBuy(Long userId, BigDecimal actualCost, BigDecimal reservedAmount) {
        User user = userRepository.findByIdWithLock(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found!"));

        BigDecimal newBalance = user.getBalance().subtract(actualCost);
        if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new InsufficientFundsException(
                    "Insufficient balance at settlement: actual cost " + actualCost
                            + " exceeds balance " + user.getBalance());
        }

        user.setBalance(newBalance);
        user.setReserved(user.getReserved().subtract(reservedAmount));
        userRepository.save(user);
    }

    // Release cash reserved at admission without debiting (order rejected/failed).
    public void releaseReservation(Long userId, BigDecimal reservedAmount) {
        User user = userRepository.findByIdWithLock(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found!"));
        user.setReserved(user.getReserved().subtract(reservedAmount));
        userRepository.save(user);
    }

    public BigDecimal increaseBalance(Long userId, BigDecimal amount) {
        User user = userRepository.findByIdWithLock(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        BigDecimal newBalance = user.getBalance().add(amount);
        user.setBalance(newBalance);
        userRepository.save(user);
        return newBalance;
    }

    public String findEmail(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found!"));

        return user.getEmail();
    }

    public String getUsername(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found!"))
                .getUsername();
    }

    public BigDecimal getBalance(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found!"))
                .getBalance();
    }
}
