package com.example.reactiveorderapi.security.config;

import com.example.reactiveorderapi.security.jwt.BearerTokenConverter;
import com.example.reactiveorderapi.security.jwt.JwtAuthenticationManager;
import com.example.reactiveorderapi.security.jwt.JwtService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UserDetailsRepositoryReactiveAuthenticationManager;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import org.springframework.security.web.server.authentication.ServerAuthenticationEntryPointFailureHandler;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;

/**
 * Configuración de seguridad para WebFlux (no la de Spring MVC): {@link SecurityWebFilterChain},
 * {@link ServerHttpSecurity} y filtros basados en {@code ServerWebExchange}.
 */
@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_DOCS = {
            "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/webjars/**"
    };

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http,
                                                         JwtService jwtService,
                                                         ReactiveUserDetailsService userDetailsService,
                                                         JsonAuthenticationEntryPoint entryPoint,
                                                         JsonAccessDeniedHandler accessDeniedHandler) {
        return http
                // CSRF deshabilitado de forma deliberada: la API es stateless y solo se autentica con
                // el header "Authorization: Bearer". No hay cookies de sesión que el navegador envíe
                // automáticamente, así que un ataque CSRF no tiene credenciales que aprovechar.
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                // Stateless: el SecurityContext no se guarda en ninguna sesión (no se crea WebSession)
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login").permitAll()
                        .pathMatchers(PUBLIC_DOCS).permitAll()
                        .pathMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .pathMatchers("/actuator/**").hasRole("ADMIN")
                        .pathMatchers(HttpMethod.GET, "/api/v1/products/**", "/api/v1/inventory/**").authenticated()
                        .pathMatchers("/api/v1/products/**", "/api/v1/inventory/**").hasRole("ADMIN")
                        .anyExchange().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterAt(jwtAuthenticationFilter(jwtService, userDetailsService, entryPoint),
                        SecurityWebFiltersOrder.AUTHENTICATION)
                .build();
    }

    /**
     * Filtro JWT: el converter extrae el Bearer token y el {@link JwtAuthenticationManager} lo
     * valida. Si el token no es válido, responde 401 con el mismo formato que el resto de errores.
     */
    private AuthenticationWebFilter jwtAuthenticationFilter(JwtService jwtService,
                                                            ReactiveUserDetailsService userDetailsService,
                                                            JsonAuthenticationEntryPoint entryPoint) {
        AuthenticationWebFilter filter =
                new AuthenticationWebFilter(new JwtAuthenticationManager(jwtService, userDetailsService));
        filter.setServerAuthenticationConverter(new BearerTokenConverter());
        filter.setAuthenticationFailureHandler(new ServerAuthenticationEntryPointFailureHandler(entryPoint));
        filter.setSecurityContextRepository(NoOpServerSecurityContextRepository.getInstance());
        return filter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Autenticación de login (email + password). Comprueba el hash BCrypt en
     * {@code Schedulers.boundedElastic()} para no ocupar el event loop con trabajo de CPU costoso.
     */
    @Bean
    public ReactiveAuthenticationManager loginAuthenticationManager(ReactiveUserDetailsService userDetailsService,
                                                                    PasswordEncoder passwordEncoder) {
        var manager = new UserDetailsRepositoryReactiveAuthenticationManager(userDetailsService);
        manager.setPasswordEncoder(passwordEncoder);
        return manager;
    }
}
