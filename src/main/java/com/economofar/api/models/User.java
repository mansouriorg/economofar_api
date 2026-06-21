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
@Table(name = "_user") // On utilise "_user" car "user" est un mot réservé en SQL PostgreSQL
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(length = 100)
    private String firstName;

    @Column(length = 100)
    private String lastName;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    private Role role;

    /**
     * Nom de l'entreprise de l'utilisateur.
     * Peut être vide si l'utilisateur n'est pas rattaché à une société.
     */
    @Column(length = 150)
    private String companyName;

    /**
     * Devise préférée de l'utilisateur.
     * Exemples : EUR, USD, GBP, TND.
     */
    @Builder.Default
    @Column(nullable = false, length = 10)
    private String preferredCurrency = "EUR";

    /**
     * Langue préférée de l'utilisateur.
     * Exemples : fr, en, ar.
     */
    @Builder.Default
    @Column(nullable = false, length = 10)
    private String language = "fr";

    // --- MÉTHODES OBLIGATOIRES DE SPRING SECURITY UserDetails ---

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (role == null) {
            return List.of();
        }

        return List.of(new SimpleGrantedAuthority(role.name()));
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return email; // L'email sert d'identifiant pour la connexion
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
        return true; // Le compte est activé par défaut
    }
}