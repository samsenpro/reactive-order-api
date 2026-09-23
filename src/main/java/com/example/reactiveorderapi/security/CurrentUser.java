package com.example.reactiveorderapi.security;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Acceso al usuario autenticado de la petición actual.
 * <p>
 * En WebFlux no hay un hilo por petición, así que no existe un {@code ThreadLocal} fiable.
 * {@link ReactiveSecurityContextHolder} lee el {@code SecurityContext} del <b>Context de Reactor</b>,
 * donde lo deja el {@code AuthenticationWebFilter} y que viaja con la suscripción de la cadena.
 */
@Component
public class CurrentUser {

    public Mono<AuthenticatedUser> get() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(Authentication::getPrincipal)
                .cast(AuthenticatedUser.class)
                .switchIfEmpty(Mono.error(() -> new AuthenticationCredentialsNotFoundException("Not authenticated")));
    }
}
