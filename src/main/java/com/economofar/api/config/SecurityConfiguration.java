package com.economofar.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable) // Désactive la protection CSRF (obligatoire pour les API mobiles)
            .authorizeHttpRequests(auth -> auth
                // ✅ AUTORISATIONS PUBLIQUES (Ouvert à tous) :
                // 1. /api/auth/** : Pour l'inscription et le login
                // 2. /uploads/** : Pour que l'iPhone puisse télécharger/afficher l'image après upload
                // 3. /api/images/** : ⚡️ AJOUTÉ ICI ! Pour permettre l'upload du ticket sans être bloqué
                .requestMatchers("/api/auth/**", "/uploads/**", "/api/images/**").permitAll()
                
                // 🔒 TOUT LE RESTE NÉCESSITE UNE CONNEXION :
                .anyRequest().authenticated()
            );
            
        return http.build();
    }
}