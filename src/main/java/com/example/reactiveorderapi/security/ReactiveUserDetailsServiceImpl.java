package com.example.reactiveorderapi.security;

import com.example.reactiveorderapi.common.Emails;
import com.example.reactiveorderapi.user.repository.UserRepository;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Carga el usuario por email de forma no bloqueante (R2DBC). Lo usan el login y el filtro JWT.
 */
@Service
public class ReactiveUserDetailsServiceImpl implements ReactiveUserDetailsService {

    private final UserRepository userRepository;

    public ReactiveUserDetailsServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public Mono<UserDetails> findByUsername(String email) {
        return userRepository.findByEmail(Emails.normalize(email))
                .map(AuthenticatedUser::from);
    }
}
