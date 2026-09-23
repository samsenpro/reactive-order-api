package com.example.reactiveorderapi.security.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Limitador de ventana fija en memoria, por clave (IP). Sin bloqueos: usa {@code ConcurrentHashMap.compute}.
 * <p>
 * Es deliberadamente simple y local a cada instancia. Con varias réplicas habría que moverlo
 * a un almacén compartido (p. ej. Redis) o al API gateway.
 */
public class FixedWindowRateLimiter {

    /** Si se superan, se purgan las ventanas caducadas para acotar la memoria. */
    private static final int MAX_TRACKED_KEYS = 10_000;

    private final int maxRequests;
    private final long windowMillis;
    private final Clock clock;
    private final ConcurrentMap<String, Window> windows = new ConcurrentHashMap<>();

    public FixedWindowRateLimiter(int maxRequests, Duration window, Clock clock) {
        this.maxRequests = maxRequests;
        this.windowMillis = window.toMillis();
        this.clock = clock;
    }

    /**
     * Registra una petición. Devuelve vacío si se permite, o el tiempo hasta que se reinicia la
     * ventana si se superó el límite.
     */
    public Optional<Duration> tryAcquire(String key) {
        long now = clock.millis();
        if (windows.size() > MAX_TRACKED_KEYS) {
            windows.values().removeIf(window -> window.isExpired(now, windowMillis));
        }
        Window window = windows.compute(key, (k, current) ->
                current == null || current.isExpired(now, windowMillis)
                        ? new Window(now, 1)
                        : current.increment());
        if (window.count() <= maxRequests) {
            return Optional.empty();
        }
        return Optional.of(Duration.ofMillis(window.startMillis() + windowMillis - now));
    }

    private record Window(long startMillis, int count) {

        Window increment() {
            return new Window(startMillis, count + 1);
        }

        boolean isExpired(long now, long windowMillis) {
            return now - startMillis >= windowMillis;
        }
    }
}
