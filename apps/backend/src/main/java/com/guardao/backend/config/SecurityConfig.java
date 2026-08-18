package com.guardao.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * GUA-8 — Configuracion minima de Spring Security.
 *
 * ATENCION: esta configuracion es PROVISIONAL.
 *
 * Al agregar spring-boot-starter-security, Spring bloquea todos los endpoints
 * por defecto con basic auth. Esto dejaria Swagger UI inaccesible y romperia
 * el criterio de aceptacion de GUA-8.
 *
 * Aqui solo se abre lo necesario para verificar que la API responde.
 * La cadena de filtros definitiva (filtro JWT, rutas publicas vs. protegidas,
 * roles) se implementa en GUA-11 y REEMPLAZA por completo a esta clase.
 *
 * No construir nada encima de esta configuracion.
 *
 * NOTA sobre el WARN "Using generated security password" en el arranque:
 * Spring lo imprime porque todavia no existe un UserDetailsService y crea un
 * usuario en memoria que no usamos. Desaparece solo en GUA-11, al declarar el
 * UserDetailsService real. No hace falta excluir la autoconfiguracion.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // Sin estado: no hay sesion de servidor, el contexto viaja en el JWT (GUA-11)
                .csrf(csrf -> csrf.disable())
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/actuator/health")
                        .permitAll()
                        // PROVISIONAL: se cierra en GUA-11
                        .anyRequest().permitAll());

        return http.build();
    }
}
