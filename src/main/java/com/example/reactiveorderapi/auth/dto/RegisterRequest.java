package com.example.reactiveorderapi.auth.dto;

import com.example.reactiveorderapi.common.PasswordPolicy;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @Schema(example = "Jane Doe")
        @NotBlank @Size(max = 100)
        String name,

        @Schema(example = "user@example.com")
        @NotBlank @Email @Size(max = 255)
        String email,

        @Schema(example = "Password123!")
        @NotBlank
        @Size(max = PasswordPolicy.MAX_LENGTH)
        @Pattern(regexp = PasswordPolicy.REGEX, message = PasswordPolicy.MESSAGE)
        String password
) {

    @Override
    public String toString() {
        return "RegisterRequest{email=" + email + "}";
    }
}
