package com.example.reactiveorderapi.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @Schema(example = "user@example.com")
        @NotBlank @Size(max = 255)
        String email,

        @Schema(example = "Password123!")
        @NotBlank @Size(max = 128)
        String password
) {

    @Override
    public String toString() {
        return "LoginRequest{email=" + email + "}";
    }
}
