package com.example.reactiveorderapi.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;

import java.time.Clock;

@Configuration
public class PersistenceConfig {

    /**
     * Operador de transacciones reactivas sobre el {@code R2dbcTransactionManager} que
     * autoconfigura Spring Boot. Se usa de forma explícita ({@code .as(transactionalOperator::transactional)})
     * para que el alcance de cada transacción quede visible en el código.
     */
    @Bean
    public TransactionalOperator transactionalOperator(ReactiveTransactionManager transactionManager) {
        return TransactionalOperator.create(transactionManager);
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
