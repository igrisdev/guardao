package com.guardao.backend.shared.error;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * GUA-12 — Errores de seguridad con el mismo formato que el resto de la API.
 *
 * Cuando falta el token o el rol no alcanza, Spring Security corta la
 * peticion dentro de la cadena de filtros, antes de que exista un
 * controlador. Por eso el @RestControllerAdvice nunca los ve: sin esta
 * clase, un 401 devuelve el HTML de error de Tomcat o un cuerpo vacio,
 * mientras el resto de la API devuelve JSON.
 *
 * El frontend tendria entonces que manejar dos formas distintas de error
 * segun donde falle. Aqui se unifican.
 *
 * OJO con el import: Spring Boot 4 usa Jackson 3, cuyo ObjectMapper vive en
 * tools.jackson.databind. El com.fasterxml.jackson.databind.ObjectMapper de
 * Jackson 2 tambien esta en el classpath (lo arrastra springdoc), pero no hay
 * bean de ese tipo. Las anotaciones si siguen en com.fasterxml.
 */
@Component
public class SecurityErrorResponder implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;
    private final ZoneId zone;

    public SecurityErrorResponder(ObjectMapper objectMapper,
            @Value("${guardao.timezone}") String timezone) {
        this.objectMapper = objectMapper;
        this.zone = ZoneId.of(timezone);
    }

    /** Sin token, token invalido o caducado. */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        write(response, ErrorCode.UNAUTHENTICATED);
    }

    /** Token valido, pero el rol no alcanza. */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {
        write(response, ErrorCode.FORBIDDEN);
    }

    private void write(HttpServletResponse response, ErrorCode code) throws IOException {
        // No se detalla por que fallo el token: decir "firma invalida" o
        // "caducado" le da pistas a quien esta probando tokens.
        ApiError body = ApiError.of(code, code.defaultMessage(), null,
                OffsetDateTime.now(zone).truncatedTo(ChronoUnit.SECONDS));

        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), body);
    }
}
