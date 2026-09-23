package com.example.reactiveorderapi.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Reactive Order API",
                version = "1.0",
                description = """
                        API reactiva de pedidos con Spring WebFlux, Project Reactor, R2DBC y PostgreSQL.

                        **Cómo autenticarse:** registra un usuario en `POST /api/v1/auth/register`, obtén un token en \
                        `POST /api/v1/auth/login` y pégalo en **Authorize** (sin el prefijo `Bearer`).""",
                license = @License(name = "MIT", url = "https://opensource.org/licenses/MIT")
        ),
        security = @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
)
@SecurityScheme(
        name = OpenApiConfig.BEARER_SCHEME,
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT"
)
public class OpenApiConfig {

    public static final String BEARER_SCHEME = "bearerAuth";

    private static final String JSON = "application/json";

    /** Ejemplos de error comunes, con el formato real de {@code ApiError}, añadidos a cada operación. */
    private static final Map<String, String[]> COMMON_ERRORS = Map.of(
            "400", new String[]{"Validation failed", """
                    {"timestamp":"2026-09-23T20:00:00Z","status":400,"error":"BAD_REQUEST","message":"Validation failed",\
                    "path":"/api/v1/orders","correlationId":"abc-123","errors":[{"field":"items","message":"must not be empty"}]}"""},
            "401", new String[]{"Missing, invalid or expired token", """
                    {"timestamp":"2026-09-23T20:00:00Z","status":401,"error":"UNAUTHORIZED","message":"Authentication required",\
                    "path":"/api/v1/orders","correlationId":"abc-123"}"""},
            "403", new String[]{"Authenticated but not allowed", """
                    {"timestamp":"2026-09-23T20:00:00Z","status":403,"error":"FORBIDDEN","message":"Access denied",\
                    "path":"/api/v1/products","correlationId":"abc-123"}"""},
            "404", new String[]{"Resource not found", """
                    {"timestamp":"2026-09-23T20:00:00Z","status":404,"error":"NOT_FOUND","message":"Product not found: 100",\
                    "path":"/api/v1/products/100","correlationId":"abc-123"}"""},
            "409", new String[]{"Conflict (insufficient stock, duplicated SKU/email...)", """
                    {"timestamp":"2026-09-23T20:00:00Z","status":409,"error":"CONFLICT",\
                    "message":"Insufficient stock for product 1 (requested 4)","path":"/api/v1/orders","correlationId":"abc-123"}"""},
            "422", new String[]{"Business rule violated", """
                    {"timestamp":"2026-09-23T20:00:00Z","status":422,"error":"UNPROCESSABLE_ENTITY",\
                    "message":"Product is not active: 5","path":"/api/v1/orders","correlationId":"abc-123"}"""},
            "500", new String[]{"Unexpected error", """
                    {"timestamp":"2026-09-23T20:00:00Z","status":500,"error":"INTERNAL_SERVER_ERROR",\
                    "message":"An unexpected error occurred","path":"/api/v1/orders","correlationId":"abc-123"}"""}
    );

    @Bean
    public OperationCustomizer commonErrorResponses() {
        return (operation, handlerMethod) -> {
            ApiResponses responses = operation.getResponses();
            COMMON_ERRORS.forEach((code, description) -> responses.putIfAbsent(code, errorResponse(description)));
            return operation;
        };
    }

    private static ApiResponse errorResponse(String[] description) {
        Example example = new Example().value(description[1]);
        return new ApiResponse()
                .description(description[0])
                .content(new Content().addMediaType(JSON, new MediaType().addExamples("example", example)));
    }
}
