package com.economofar.api.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "_user")
public class User implements UserDetails { // ✅ 1. On ajoute "implements UserDetails"

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

    // ✅ 2. On ajoute les méthodes obligatoires demandées par Spring Security

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // Transforme le rôle (ADMIN, STAFF) en autorisation Spring
        return List.of(new SimpleGrantedAuthority(role.name()));
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return email; // Pour nous, le "nom d'utilisateur" C'EST l'email
    }

    @Override
    public boolean isAccountNonExpired() {
        return true; // Le compte n'expire jamais
    }

    @Override
    public boolean isAccountNonLocked() {
        return true; // Le compte n'est jamais verrouillé
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true; // Le mot de passe n'expire jamais
    }

    @Override
    public boolean isEnabled() {
        return true; // Le compte est activé
    }
}