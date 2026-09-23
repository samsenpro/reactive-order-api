package com.example.reactiveorderapi.security.jwt;

import com.example.reactiveorderapi.security.AuthenticatedUser;
import com.example.reactiveorderapi.user.entity.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Optional;

import static com.example.reactiveorderapi.support.Fixtures.principal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationManagerTest {

    @Mock
    private JwtService jwtService;
    @Mock
    private ReactiveUserDetailsService userDetailsService;

    @Test
    void validTokenOfEnabledUserAuthenticates() {
        AuthenticatedUser user = principal(1L, Role.ADMIN);
        when(jwtService.extractValidSubject("token")).thenReturn(Optional.of(user.email()));
        when(userDetailsService.findByUsername(user.email())).thenReturn(Mono.just(user));

        StepVerifier.create(manager().authenticate(bearer("token")))
                .assertNext(authentication -> {
                    assertThat(authentication.isAuthenticated()).isTrue();
                    assertThat(authentication.getPrincipal()).isEqualTo(user);
                    assertThat(authentication.getAuthorities()).extracting("authority").containsExactly("ROLE_ADMIN");
                })
                .verifyComplete();
    }

    @Test
    void invalidTokenFailsWithoutTouchingTheDatabase() {
        when(jwtService.extractValidSubject("bad")).thenReturn(Optional.empty());

        StepVerifier.create(manager().authenticate(bearer("bad")))
                .expectError(BadCredentialsException.class)
                .verify();
        verifyNoInteractions(userDetailsService);
    }

    @Test
    void deletedUserIsRejected() {
        when(jwtService.extractValidSubject("token")).thenReturn(Optional.of("gone@test.local"));
        when(userDetailsService.findByUsername("gone@test.local")).thenReturn(Mono.empty());

        StepVerifier.create(manager().authenticate(bearer("token")))
                .expectError(BadCredentialsException.class)
                .verify();
    }

    @Test
    void disabledUserIsRejected() {
        AuthenticatedUser disabled = new AuthenticatedUser(1L, "off@test.local", "hash", Role.USER, false);
        when(jwtService.extractValidSubject("token")).thenReturn(Optional.of(disabled.email()));
        when(userDetailsService.findByUsername(disabled.email())).thenReturn(Mono.just(disabled));

        StepVerifier.create(manager().authenticate(bearer("token")))
                .expectError(DisabledException.class)
                .verify();
    }

    private JwtAuthenticationManager manager() {
        return new JwtAuthenticationManager(jwtService, userDetailsService);
    }

    private static UsernamePasswordAuthenticationToken bearer(String token) {
        return UsernamePasswordAuthenticationToken.unauthenticated(null, token);
    }
}
