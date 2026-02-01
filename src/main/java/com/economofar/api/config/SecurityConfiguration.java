package com.economofar.api.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfiguration {

    // On injecte nos deux composants de sécurité créés précédemment
    private final JwtAuthenticationFilter jwtAuthFilter;
    private final AuthenticationProvider authenticationProvider;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable) // Désactive CSRF (inutile pour API Mobile)
            
            .authorizeHttpRequests(auth -> auth
                // ✅ ZONES PUBLIQUES (Tout le monde peut y accéder)
                .requestMatchers("/api/auth/**").permitAll() // Login & Register
                .requestMatchers("/uploads/**").permitAll()  // Voir les images
                
                // 🔒 ZONES PRIVÉES (Il faut un Token valide)
                // Note : J'ai retiré "/api/images/**" d'ici. 
                // Maintenant, l'iPhone DEVRA envoyer le token pour uploader un ticket !
                .anyRequest().authenticated()
            )
            
            // On configure la session en "Stateless" (pas de cookies, chaque requête est indépendante)
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            
            // On déclare notre fournisseur d'authentification (celui avec BCrypt)
            .authenticationProvider(authenticationProvider)
            
            // 🔥 LE PLUS IMPORTANT : On place notre filtre JWT avant le filtre classique
            // Spring va d'abord vérifier le token. S'il est bon, il laisse passer.
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}