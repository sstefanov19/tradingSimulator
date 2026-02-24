package com.example.tradingsimulator.controller;

import com.example.tradingsimulator.dto.DepositRequestDto;
import com.example.tradingsimulator.service.UserService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("api/v1/balance")
public class UserController {


    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/{userId}")
    public ResponseEntity<BigDecimal> getBalance(@PathVariable Long userId) {
        BigDecimal balance = userService.getBalance(userId);
        return ResponseEntity.ok(balance);
    }

    @PostMapping("/deposit")
        public ResponseEntity<BigDecimal> deposit(@RequestBody DepositRequestDto depositRequest) {
        BigDecimal newBalance = userService.increaseBalance(depositRequest.userId() , depositRequest.amount());

        return ResponseEntity.ok(newBalance);
    }

}
