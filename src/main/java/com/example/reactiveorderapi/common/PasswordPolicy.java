package com.example.reactiveorderapi.common;

import java.util.regex.Pattern;

/**
 * Política de contraseñas compartida por el registro y el seed de administrador.
 */
public final class PasswordPolicy {

    /** Mínimo 8 caracteres con al menos una mayúscula, una minúscula y un número. */
    public static final String REGEX = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}$";
    public static final String MESSAGE =
            "must be at least 8 characters long and contain an uppercase letter, a lowercase letter and a number";
    /** BCrypt solo tiene en cuenta los primeros 72 bytes. */
    public static final int MAX_LENGTH = 72;

    private static final Pattern PATTERN = Pattern.compile(REGEX);

    private PasswordPolicy() {
    }

    public static boolean isSatisfiedBy(String password) {
        return password != null && password.length() <= MAX_LENGTH && PATTERN.matcher(password).matches();
    }
}
