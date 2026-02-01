package com.economofar.api.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,     // ✅ Plus besoin de @NonNull ici
            HttpServletResponse response,   // ✅ Ni ici
            FilterChain filterChain         // ✅ Ni là
    ) throws ServletException, IOException {
        
        // 1. On cherche le header "Authorization" dans la requête de l'iPhone
        final String authHeader = request.getHeader("Authorization");
        final String jwt;
        final String userEmail;

        // Si pas de header ou si ça ne commence pas par "Bearer ", on laisse tomber
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // 2. On extrait le token (on enlève "Bearer " qui fait 7 caractères)
        jwt = authHeader.substring(7);
        
        // 3. On extrait l'email de l'utilisateur caché dans le token
        userEmail = jwtService.extractUsername(jwt);

        // 4. Si on a trouvé un email et que l'utilisateur n'est pas encore connecté dans le contexte
        if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            
            // On charge les détails de l'utilisateur depuis la base de données
            UserDetails userDetails = this.userDetailsService.loadUserByUsername(userEmail);
            
            // 5. On vérifie si le token est valide (bon user + pas expiré)
            if (jwtService.isTokenValid(jwt, userDetails)) {
                
                // 6. C'est valide ! On crée l'objet d'authentification officiel
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities()
                );
                
                authToken.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request)
                );
                
                // 7. On met à jour le contexte de sécurité de Spring
                // À partir de maintenant, Spring sait qui est connecté !
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }
        
        // On passe à la suite de la chaîne (le Controller)
        filterChain.doFilter(request, response);
    }
}