package com.example.reactiveorderapi.auth.service;

import com.example.reactiveorderapi.auth.dto.LoginRequest;
import com.example.reactiveorderapi.auth.dto.RegisterRequest;
import com.example.reactiveorderapi.exception.DuplicateResourceException;
import com.example.reactiveorderapi.exception.InvalidCredentialsException;
import com.example.reactiveorderapi.security.AuthenticatedUser;
import com.example.reactiveorderapi.security.jwt.JwtService;
import com.example.reactiveorderapi.user.entity.Role;
import com.example.reactiveorderapi.user.entity.User;
import com.example.reactiveorderapi.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Clock;
import java.time.ZoneOffset;

import static com.example.reactiveorderapi.support.Fixtures.NOW;
import static com.example.reactiveorderapi.support.Fixtures.principal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private ReactiveAuthenticationManager authenticationManager;
    @Mock
    private JwtService jwtService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, authenticationManager, jwtService,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void registerNormalizesEmailHashesPasswordAndAssignsUserRole() {
        when(userRepository.existsByEmail("jane@example.com")).thenReturn(Mono.just(false));
        when(passwordEncoder.encode("Password123")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            return Mono.just(new User(5L, user.name(), user.email(), user.password(), user.role(), true, NOW, NOW));
        });

        StepVerifier.create(authService.register(new RegisterRequest(" Jane ", " Jane@Example.COM ", "Password123")))
                .assertNext(response -> {
                    assertThat(response.id()).isEqualTo(5L);
                    assertThat(response.email()).isEqualTo("jane@example.com");
                    assertThat(response.role()).isEqualTo(Role.USER);
                })
                .verifyComplete();

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().password()).isEqualTo("hashed");
        assertThat(saved.getValue().name()).isEqualTo("Jane");
    }

    @Test
    void registerRejectsExistingEmail() {
        when(userRepository.existsByEmail("jane@example.com")).thenReturn(Mono.just(true));

        StepVerifier.create(authService.register(new RegisterRequest("Jane", "jane@example.com", "Password123")))
                .expectError(DuplicateResourceException.class)
                .verify();
        verify(userRepository, never()).save(any());
    }

    @Test
    void concurrentDuplicateRegistrationBecomesConflict() {
        when(userRepository.existsByEmail("jane@example.com")).thenReturn(Mono.just(false));
        when(passwordEncoder.encode("Password123")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenReturn(Mono.error(new DuplicateKeyException("dup")));

        StepVerifier.create(authService.register(new RegisterRequest("Jane", "jane@example.com", "Password123")))
                .expectError(DuplicateResourceException.class)
                .verify();
    }

    @Test
    void loginIssuesBearerToken() {
        AuthenticatedUser user = principal(5L, Role.USER);
        Authentication authenticated = UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities());
        when(authenticationManager.authenticate(any())).thenReturn(Mono.just(authenticated));
        when(jwtService.generateToken(user)).thenReturn("jwt");
        when(jwtService.getExpirationSeconds()).thenReturn(3600L);

        StepVerifier.create(authService.login(new LoginRequest("User5@Test.local", "Password123")))
                .assertNext(response -> {
                    assertThat(response.accessToken()).isEqualTo("jwt");
                    assertThat(response.tokenType()).isEqualTo("Bearer");
                    assertThat(response.expiresIn()).isEqualTo(3600L);
                })
                .verifyComplete();

        ArgumentCaptor<Authentication> attempt = ArgumentCaptor.forClass(Authentication.class);
        verify(authenticationManager).authenticate(attempt.capture());
        assertThat(attempt.getValue().getName()).isEqualTo("user5@test.local");
    }

    @Test
    void anyAuthenticationFailureBecomesTheSameGenericError() {
        when(authenticationManager.authenticate(any()))
                .thenReturn(Mono.error(new BadCredentialsException("bad")))
                .thenReturn(Mono.error(new DisabledException("disabled")));

        StepVerifier.create(authService.login(new LoginRequest("a@b.c", "x")))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(InvalidCredentialsException.class)
                        .hasMessage("Invalid email or password"))
                .verify();
        StepVerifier.create(authService.login(new LoginRequest("a@b.c", "x")))
                .expectError(InvalidCredentialsException.class)
                .verify();
    }
}
