package com.example.tradingsimulator.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data

@NoArgsConstructor

@AllArgsConstructor

@Builder

@Entity

@Table(name = "application_user")
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

    public User(String fullName, String username, String password, Role role) {
        this.fullName = fullName;
        this.username = username;
        this.password = password;
        this.role = role;
    }
}
