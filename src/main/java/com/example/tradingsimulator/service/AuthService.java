package com.example.tradingsimulator.service;

import com.example.tradingsimulator.dto.LoginRequest;
import com.example.tradingsimulator.dto.RefreshTokenRequest;
import com.example.tradingsimulator.dto.RegisterRequest;
import com.example.tradingsimulator.dto.TokenPair;
import com.example.tradingsimulator.model.User;
import com.example.tradingsimulator.repository.UserRepository;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@AllArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    private final AuthenticationManager authenticationManager;
    private final EmailSenderService emailSenderService;
    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    @Transactional
    public void registerUser(RegisterRequest registerRequest) {
        if(userRepository.existsByUsername(registerRequest.getUsername())) {
            throw new IllegalArgumentException("User already exists!");
        }

        User user = User.builder()
                .email(registerRequest.getEmail())
                .username(registerRequest.getUsername())
                .password(passwordEncoder.encode(registerRequest.getPassword()))
                .role(registerRequest.getRole())
                .balance(BigDecimal.ZERO)
                .build();

        emailSenderService.sendEmail(user.getEmail(), "Register User" ,
                "Thank you for your trust to sign up for TradingSimulator.Where you can explore" +
                        "the world of the stock market." +
                        "Happy scrolling!");
        userRepository.save(user);
    }

    public TokenPair login(LoginRequest loginRequest) {
        // Authenticate
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.getUsername(),
                        loginRequest.getPassword()
                )
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        return jwtService.generateTokenPair(authentication);

    }

    public TokenPair refreshToken(@Valid RefreshTokenRequest request) {
       String refreshToken = request.getRefreshToken();

        if(!jwtService.isRefreshToken(refreshToken)){
            throw new IllegalArgumentException("Invalid refresh token!");
        }

        String user = jwtService.extractUsernameFromToken(refreshToken);
        UserDetails userDetails = userDetailsService.loadUserByUsername(user);

        if(userDetails == null) {
            throw new IllegalArgumentException("User not found!");
        }

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                userDetails,
                null,
                userDetails.getAuthorities()
        );

        String accessToken = jwtService.generateAccessToken(authentication);
        return new TokenPair(accessToken, refreshToken);

    }
}
