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
            name = "email",
            nullable = false
    )
    private String email;



    @Column(
            unique = true,
            nullable = false
    )
    private String username;


    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    private Role role;


    private BigDecimal balance;

    public User(String email, String username, String password, Role role )  {
        this.email = email;
        this.username = username;
        this.password = password;
        this.role = role;
    }
}
