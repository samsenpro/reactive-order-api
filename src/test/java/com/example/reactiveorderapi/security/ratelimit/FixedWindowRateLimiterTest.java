package com.example.reactiveorderapi.security.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class FixedWindowRateLimiterTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-09-23T10:00:00Z"));
    private final FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(2, Duration.ofMinutes(1), clock);

    @Test
    void allowsUpToTheLimitThenRejectsWithRetryAfter() {
        assertThat(limiter.tryAcquire("1.2.3.4")).isEmpty();
        assertThat(limiter.tryAcquire("1.2.3.4")).isEmpty();

        clock.advance(Duration.ofSeconds(20));
        assertThat(limiter.tryAcquire("1.2.3.4")).contains(Duration.ofSeconds(40));
    }

    @Test
    void keysAreIndependent() {
        limiter.tryAcquire("a");
        limiter.tryAcquire("a");

        assertThat(limiter.tryAcquire("a")).isPresent();
        assertThat(limiter.tryAcquire("b")).isEmpty();
    }

    @Test
    void windowResetsAfterItExpires() {
        limiter.tryAcquire("a");
        limiter.tryAcquire("a");
        clock.advance(Duration.ofMinutes(1));

        assertThat(limiter.tryAcquire("a")).isEmpty();
    }

    private static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
