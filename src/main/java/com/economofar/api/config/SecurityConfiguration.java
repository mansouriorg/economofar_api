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
            .csrf(AbstractHttpConfigurer::disable) // Désactive la protection CSRF
            .authorizeHttpRequests(auth -> auth
                // ✅ AUTORISATIONS PUBLIQUES :
                // 1. /api/auth/** : Pour s'inscrire et se connecter
                // 2. /uploads/** : Pour afficher les images des tickets
                .requestMatchers("/api/auth/**", "/uploads/**").permitAll()
                
                // 🔒 TOUT LE RESTE EST PRIVÉ :
                .anyRequest().authenticated()
            );
            
        return http.build();
    }
}