package com.guardao.backend.shared.config;

import javax.crypto.spec.SecretKeySpec;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;

import com.guardao.backend.auth.AccessTokenAuthenticationConverter;
import com.guardao.backend.auth.JwtProperties;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

/**
 * GUA-11 — Cadena de filtros y firma de tokens.
 *
 * Reemplaza la configuracion provisional de GUA-8.
 *
 * Decisiones:
 *
 * - Firma simetrica HS256. Solo Guardao emite y valida estos tokens; una
 *   pareja de llaves publica/privada agregaria rotacion y distribucion sin
 *   resolver nada que hoy necesitemos.
 *
 * - Sin estado. El contexto viaja en el token, asi que se pueden correr
 *   varias instancias del backend sin sesiones compartidas.
 *
 * - Lista blanca de rutas publicas. Todo lo que no este declarado abajo
 *   exige autenticacion. Si alguien agrega un endpoint y olvida la regla,
 *   queda protegido por omision, que es el fallo seguro.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    /**
     * Rutas abiertas sin token.
     *
     * Todas ellas quedan expuestas a internet, asi que necesitan limitacion
     * de peticiones antes de salir a produccion (System Design 5.2).
     */
    private static final String[] PUBLIC_ENDPOINTS = {
            // Registro de barberia, login y refresco (GUA-20, GUA-21)
            "/api/v1/auth/**",
            // Pagina publica de reservas: datos del negocio, disponibilidad, reservar
            "/api/v1/public/**",
            // Gestion de la cita por enlace privado; el manage_token es la credencial
            "/api/v1/appointments/manage/**",
            // Webhooks: se autentican validando la firma del proveedor, no con JWT
            "/api/v1/webhooks/**",
            // Documentacion. Ya viene deshabilitada en el perfil prod
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/actuator/health"
    };

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtDecoder jwtDecoder) throws Exception {
        http
                // No hay cookies de sesion: el token viaja en el header
                // Authorization, que no se envia solo. CSRF no aplica.
                .csrf(csrf -> csrf.disable())
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(auth -> auth
                        // El preflight de CORS viaja sin token
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        // Por omision: todo lo demas exige token valido
                        .anyRequest().authenticated())

                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .decoder(jwtDecoder)
                                .jwtAuthenticationConverter(new AccessTokenAuthenticationConverter())));

        return http.build();
    }

    @Bean
    public JwtEncoder jwtEncoder(JwtProperties properties) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey(properties)));
    }

    @Bean
    public JwtDecoder jwtDecoder(JwtProperties properties) {
        return NimbusJwtDecoder.withSecretKey(secretKey(properties))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    /**
     * BCrypt para las contrasenas de los usuarios del dashboard (GUA-20).
     * Nunca se guarda una contrasena en claro ni con un hash rapido.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private static SecretKeySpec secretKey(JwtProperties properties) {
        return new SecretKeySpec(properties.secret().getBytes(), "HmacSHA256");
    }
}
