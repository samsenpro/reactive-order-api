package com.example.reactiveorderapi.auth.service;

import com.example.reactiveorderapi.auth.dto.AuthResponse;
import com.example.reactiveorderapi.auth.dto.LoginRequest;
import com.example.reactiveorderapi.auth.dto.RegisterRequest;
import com.example.reactiveorderapi.common.Emails;
import com.example.reactiveorderapi.exception.DuplicateResourceException;
import com.example.reactiveorderapi.exception.InvalidCredentialsException;
import com.example.reactiveorderapi.security.AuthenticatedUser;
import com.example.reactiveorderapi.security.jwt.JwtService;
import com.example.reactiveorderapi.user.dto.UserResponse;
import com.example.reactiveorderapi.user.entity.Role;
import com.example.reactiveorderapi.user.entity.User;
import com.example.reactiveorderapi.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Clock;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ReactiveAuthenticationManager loginAuthenticationManager;
    private final JwtService jwtService;
    private final Clock clock;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       ReactiveAuthenticationManager loginAuthenticationManager,
                       JwtService jwtService,
                       Clock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.loginAuthenticationManager = loginAuthenticationManager;
        this.jwtService = jwtService;
        this.clock = clock;
    }

    public Mono<UserResponse> register(RegisterRequest request) {
        String email = Emails.normalize(request.email());
        return userRepository.existsByEmail(email)
                .flatMap(exists -> exists ? Mono.error(emailTaken()) : encode(request.password()))
                .map(hash -> User.create(request.name().trim(), email, hash, Role.USER, clock.instant()))
                .flatMap(userRepository::save)
                // Carrera entre dos registros simultáneos: decide el índice único de la BD
                .onErrorMap(DuplicateKeyException.class, ex -> emailTaken())
                .doOnNext(user -> log.info("User registered userId={}", user.id()))
                .map(UserResponse::from);
    }

    /**
     * Email inexistente, contraseña incorrecta y cuenta bloqueada producen el mismo 401
     * para no permitir la enumeración de usuarios.
     */
    public Mono<AuthResponse> login(LoginRequest request) {
        return loginAuthenticationManager
                .authenticate(UsernamePasswordAuthenticationToken.unauthenticated(
                        Emails.normalize(request.email()), request.password()))
                .map(authentication -> (AuthenticatedUser) authentication.getPrincipal())
                .doOnNext(user -> log.info("User logged in userId={}", user.id()))
                .map(user -> AuthResponse.bearer(jwtService.generateToken(user), jwtService.getExpirationSeconds()))
                .onErrorMap(AuthenticationException.class, ex -> {
                    log.info("Failed login attempt ({})", ex.getClass().getSimpleName());
                    return new InvalidCredentialsException();
                });
    }

    /**
     * BCrypt es costoso a propósito (~100 ms de CPU). Se ejecuta en {@code boundedElastic}
     * para no bloquear el event loop de Netty mientras se calcula el hash.
     */
    private Mono<String> encode(String rawPassword) {
        return Mono.fromCallable(() -> passwordEncoder.encode(rawPassword))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private static DuplicateResourceException emailTaken() {
        return new DuplicateResourceException("Email is already registered");
    }
}
