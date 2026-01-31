package com.economofar.api.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data // Lombok génère les Getters/Setters automatiquement
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "_user") // "user" est un mot réservé SQL, donc on utilise "_user"
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, nullable = false)
    private String email;

    private String firstName;
    private String lastName;
    private String password;

    @Enumerated(EnumType.STRING)
    private Role role;
}