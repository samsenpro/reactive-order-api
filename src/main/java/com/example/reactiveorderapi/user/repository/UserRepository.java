package com.example.reactiveorderapi.user.repository;

import com.example.reactiveorderapi.user.entity.User;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Mono;

public interface UserRepository extends R2dbcRepository<User, Long> {

    Mono<User> findByEmail(String email);

    Mono<Boolean> existsByEmail(String email);
}
