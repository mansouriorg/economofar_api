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
@Table(name = "_user") // On utilise "_user" car "user" est un mot réservé en SQL (PostgreSQL)
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID) // Génère automatiquement un ID unique complexe
    private UUID id;

    @Column(unique = true, nullable = false)
    private String email;

    private String firstName;
    
    private String lastName;
    
    private String password;

    @Enumerated(EnumType.STRING) // Stocke le rôle sous forme de texte ("ADMIN", "USER") en base
    private Role role;

    // --- MÉTHODES OBLIGATOIRES DE SPRING SECURITY (UserDetails) ---

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // Retourne la liste des permissions. Ici, on transforme l'enum Role en "Autorité"
        // ex: Si role = ADMIN, l'autorité sera "ADMIN" (ou "ROLE_ADMIN" selon config)
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