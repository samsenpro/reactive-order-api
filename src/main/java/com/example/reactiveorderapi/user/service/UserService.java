package com.example.reactiveorderapi.user.service;

import com.example.reactiveorderapi.exception.UserNotFoundException;
import com.example.reactiveorderapi.user.entity.User;
import com.example.reactiveorderapi.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Devuelve el usuario si existe y está habilitado; en otro caso emite {@link UserNotFoundException}.
     */
    public Mono<User> requireActiveUser(Long userId) {
        return userRepository.findById(userId)
                .filter(User::enabled)
                .switchIfEmpty(Mono.error(() -> new UserNotFoundException(userId)));
    }
}
