package com.example.tradingsimulator.dto;

import com.example.tradingsimulator.model.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data

@Builder

@NoArgsConstructor

@AllArgsConstructor
public class RegisterRequest {

    @NotBlank(message = "Email is required")
    @Size(min = 3,  message = "Full name must be between 3 and 50 characters")
    @Email(message = "Invalid email format")
    private String email;


    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 10, message = "Username must be between 3 and 10 characters")

    private String username;

    @NotBlank(message = "Password is required")
    @Size(min = 3, message = "Password must be at least 3 characters")

    private String password;

    private Role role;

}
