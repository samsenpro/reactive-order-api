package com.example.reactiveorderapi.security.jwt;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.core.userdetails.UserDetails;
import reactor.core.publisher.Mono;

/**
 * Valida el JWT y carga el usuario de la BD en cada petición: bloquear o borrar una cuenta
 * invalida sus tokens al instante, sin esperar a que expiren.
 */
public class JwtAuthenticationManager implements ReactiveAuthenticationManager {

    private final JwtService jwtService;
    private final ReactiveUserDetailsService userDetailsService;

    public JwtAuthenticationManager(JwtService jwtService, ReactiveUserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    public Mono<Authentication> authenticate(Authentication authentication) {
        String token = (String) authentication.getCredentials();
        return Mono.justOrEmpty(jwtService.extractValidSubject(token))
                .switchIfEmpty(Mono.error(() -> new BadCredentialsException("Invalid token")))
                .flatMap(userDetailsService::findByUsername)
                .switchIfEmpty(Mono.error(() -> new BadCredentialsException("Unknown user")))
                .filter(UserDetails::isEnabled)
                .switchIfEmpty(Mono.error(() -> new DisabledException("Account disabled")))
                .map(user -> UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities()));
    }
}
