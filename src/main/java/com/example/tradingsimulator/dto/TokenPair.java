package com.example.tradingsimulator.dto;

public record TokenPair(
        String accessToken,
        String refreshToken,
        Long userId
) {}
