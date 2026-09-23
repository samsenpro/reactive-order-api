package com.example.reactiveorderapi.exception;

import com.example.reactiveorderapi.common.ApiError.FieldViolation;
import io.r2dbc.spi.R2dbcTransientResourceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.TimeoutException;

/**
 * Manejador global de errores para WebFlux. Se registra antes que el de Spring Boot
 * ({@code @Order(-2)}) y cubre tanto los errores de los controllers como los que se producen
 * en los {@code WebFilter} (por ejemplo, el límite de peticiones).
 * <p>
 * Nunca expone stack traces ni mensajes internos: los 5xx se registran en el log y el cliente
 * recibe un mensaje genérico con el correlation ID para poder rastrearlos.
 */
@Component
@Order(-2)
public class GlobalErrorWebExceptionHandler implements ErrorWebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalErrorWebExceptionHandler.class);
    private static final String VALIDATION_FAILED = "Validation failed";

    private final ApiErrorWriter errorWriter;

    public GlobalErrorWebExceptionHandler(ApiErrorWriter errorWriter) {
        this.errorWriter = errorWriter;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(ex);
        }
        ErrorDetails details = resolve(ex);
        logError(exchange, ex, details.status());
        if (ex instanceof RateLimitExceededException rateLimited) {
            exchange.getResponse().getHeaders()
                    .set(HttpHeaders.RETRY_AFTER, String.valueOf(Math.max(1, rateLimited.getRetryAfter().toSeconds())));
        }
        return errorWriter.write(exchange, details.status(), details.message(), details.violations());
    }

    private ErrorDetails resolve(Throwable ex) {
        return switch (ex) {
            case ApiException api -> ErrorDetails.of(api.getStatus(), api.getMessage());
            case WebExchangeBindException bind -> new ErrorDetails(HttpStatus.BAD_REQUEST, VALIDATION_FAILED,
                    bind.getFieldErrors().stream()
                            .map(error -> new FieldViolation(error.getField(), error.getDefaultMessage()))
                            .toList());
            case HandlerMethodValidationException validation -> new ErrorDetails(HttpStatus.BAD_REQUEST,
                    VALIDATION_FAILED,
                    validation.getParameterValidationResults().stream()
                            .flatMap(result -> result.getResolvableErrors().stream()
                                    .map(error -> new FieldViolation(
                                            result.getMethodParameter().getParameterName(),
                                            error.getDefaultMessage())))
                            .toList());
            case ServerWebInputException input -> ErrorDetails.of(HttpStatus.BAD_REQUEST,
                    "Malformed request: invalid body or parameters");
            case ResponseStatusException status -> ErrorDetails.of(toHttpStatus(status.getStatusCode()),
                    toHttpStatus(status.getStatusCode()).getReasonPhrase());
            case AuthenticationException auth -> ErrorDetails.of(HttpStatus.UNAUTHORIZED, "Authentication required");
            case AccessDeniedException denied -> ErrorDetails.of(HttpStatus.FORBIDDEN, "Access denied");
            case DataIntegrityViolationException conflict -> ErrorDetails.of(HttpStatus.CONFLICT,
                    "The request conflicts with existing data");
            case TimeoutException timeout -> ErrorDetails.of(HttpStatus.GATEWAY_TIMEOUT,
                    "A downstream operation timed out");
            case DataAccessResourceFailureException unavailable -> serviceUnavailable();
            case TransientDataAccessException unavailable -> serviceUnavailable();
            case R2dbcTransientResourceException unavailable -> serviceUnavailable();
            default -> ErrorDetails.of(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
        };
    }

    private static ErrorDetails serviceUnavailable() {
        return ErrorDetails.of(HttpStatus.SERVICE_UNAVAILABLE, "Service temporarily unavailable, please retry");
    }

    private static HttpStatus toHttpStatus(HttpStatusCode code) {
        HttpStatus status = HttpStatus.resolve(code.value());
        return status != null ? status : HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private void logError(ServerWebExchange exchange, Throwable ex, HttpStatus status) {
        String request = exchange.getRequest().getMethod() + " " + exchange.getRequest().getPath().value();
        if (status.is5xxServerError()) {
            log.error("Request failed {} -> {}", request, status.value(), ex);
        } else {
            log.debug("Request rejected {} -> {} ({})", request, status.value(), ex.getClass().getSimpleName());
        }
    }

    private record ErrorDetails(HttpStatus status, String message, List<FieldViolation> violations) {

        static ErrorDetails of(HttpStatus status, String message) {
            return new ErrorDetails(status, message, List.of());
        }
    }
}
