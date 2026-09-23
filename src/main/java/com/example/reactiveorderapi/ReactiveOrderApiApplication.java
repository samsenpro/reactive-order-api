package com.example.reactiveorderapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ReactiveOrderApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReactiveOrderApiApplication.class, args);
    }
}
