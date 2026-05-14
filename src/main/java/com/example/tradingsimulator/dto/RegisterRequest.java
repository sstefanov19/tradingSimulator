package com.example.tradingsimulator.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "Email is required")
        @Size(min = 3, message = "Full name must be between 3 and 50 characters")
        @Email(message = "Invalid email format")
        String email,

        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 10, message = "Username must be between 3 and 10 characters")
        String username,

        @NotBlank(message = "Password is required")
        @Size(min = 3, message = "Password must be at least 3 characters")
        String password
) {}
