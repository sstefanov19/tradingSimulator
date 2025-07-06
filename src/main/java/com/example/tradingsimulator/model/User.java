package com.example.tradingsimulator.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data

@NoArgsConstructor

@AllArgsConstructor

@Builder

@Entity

@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private long id;


    @Column(
            name = "full_name",
            nullable = false
    )
    private String fullName;



    @Column(
            unique = true,
            nullable = false
    )
    private String username;


    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    private Role role;

    @Column(nullable = false)
    private BigDecimal balance;

    public User(String fullName, String username, String password, Role role ,  BigDecimal balance) {
        this.fullName = fullName;
        this.username = username;
        this.password = password;
        this.role = role;
        this.balance = balance;
    }
}
