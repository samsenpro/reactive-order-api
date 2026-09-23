package com.example.reactiveorderapi.config;

import com.example.reactiveorderapi.common.Emails;
import com.example.reactiveorderapi.common.PasswordPolicy;
import com.example.reactiveorderapi.user.entity.Role;
import com.example.reactiveorderapi.user.entity.User;
import com.example.reactiveorderapi.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Clock;

/**
 * Crea el administrador inicial si se definieron ADMIN_EMAIL y ADMIN_PASSWORD y aún no existe.
 * <p>
 * Se ejecuta de forma reactiva al arrancar (sin {@code block()}): el flujo se suscribe cuando
 * la aplicación está lista. {@link #completion()} permite esperar a que termine (lo usan los tests).
 */
@Component
public class AdminInitializer {

    private static final Logger log = LoggerFactory.getLogger(AdminInitializer.class);
    private static final String DEFAULT_ADMIN_NAME = "Administrator";

    private final AdminProperties adminProperties;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final Mono<Void> seeding;

    public AdminInitializer(AdminProperties adminProperties,
                            UserRepository userRepository,
                            PasswordEncoder passwordEncoder,
                            Clock clock) {
        this.adminProperties = adminProperties;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.seeding = Mono.defer(this::seedIfNeeded).cache();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        seeding.subscribe(
                ignored -> { },
                error -> log.error("Admin seed failed: {}", error.getMessage()));
    }

    public Mono<Void> completion() {
        return seeding.onErrorComplete();
    }

    private Mono<Void> seedIfNeeded() {
        if (!adminProperties.isConfigured()) {
            log.info("Admin seed skipped: ADMIN_EMAIL / ADMIN_PASSWORD not set");
            return Mono.empty();
        }
        if (!PasswordPolicy.isSatisfiedBy(adminProperties.password())) {
            log.warn("Admin seed skipped: ADMIN_PASSWORD does not satisfy the password policy");
            return Mono.empty();
        }
        String email = Emails.normalize(adminProperties.email());
        return userRepository.existsByEmail(email)
                .filter(exists -> !exists)
                .flatMap(missing -> Mono.fromCallable(() -> passwordEncoder.encode(adminProperties.password()))
                        .subscribeOn(Schedulers.boundedElastic()))
                .map(hash -> User.create(adminName(), email, hash, Role.ADMIN, clock.instant()))
                .flatMap(userRepository::save)
                .doOnNext(admin -> log.info("Initial admin account created userId={}", admin.id()))
                .then();
    }

    private String adminName() {
        String name = adminProperties.name();
        return name == null || name.isBlank() ? DEFAULT_ADMIN_NAME : name.trim();
    }
}
