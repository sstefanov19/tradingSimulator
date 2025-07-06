package com.example.tradingsimulator.controller;

import com.example.tradingsimulator.dto.DepositRequestDto;
import com.example.tradingsimulator.service.UserService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("api/v1")
public class UserController {


    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/balance/{userId}")
    public ResponseEntity<BigDecimal> getBalance(@PathVariable String userId) {
        BigDecimal balance = userService.getBalance(userId);

        return ResponseEntity.ok(balance);
    }

    @PostMapping("/deposit/{userId}")
    public ResponseEntity<BigDecimal> deposit(@PathVariable String userId ,
                                              @RequestBody DepositRequestDto depositRequest) {
        BigDecimal amount = depositRequest.getAmount();
        BigDecimal newBalance = userService.increaseBalance(userId , amount);

        return ResponseEntity.ok(newBalance);
    }

}
