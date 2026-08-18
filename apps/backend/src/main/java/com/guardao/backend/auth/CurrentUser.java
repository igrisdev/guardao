package com.guardao.backend.auth;

import java.util.Optional;
import java.util.UUID;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * GUA-11 — Acceso al usuario autenticado desde los servicios.
 *
 * Es el unico lugar donde se traduce el JWT a AuthenticatedUser. Ningun
 * servicio debe leer claims por su cuenta: si el formato del token cambia,
 * se cambia aqui y en ningun otro sitio.
 *
 * GUA-22 usara esto para aplicar el filtrado por businessId de forma
 * automatica, en vez de dejarlo a criterio de cada consulta.
 */
@Component
public class CurrentUser {

    /**
     * @throws IllegalStateException si no hay usuario autenticado. Es
     *         intencional: llamar a esto en una ruta publica es un error de
     *         programacion, no una situacion que haya que tolerar.
     */
    public AuthenticatedUser require() {
        return find().orElseThrow(() -> new IllegalStateException(
                "No hay usuario autenticado en el contexto. "
                        + "Esta ruta deberia estar protegida."));
    }

    public Optional<AuthenticatedUser> find() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();

        if (!(authentication instanceof JwtAuthenticationToken jwtAuth)) {
            return Optional.empty();
        }

        return Optional.of(fromJwt(jwtAuth.getToken()));
    }

    /** Atajo para el caso mas frecuente: filtrar por negocio. */
    public UUID businessId() {
        return require().businessId();
    }

    static AuthenticatedUser fromJwt(Jwt jwt) {
        String staffId = jwt.getClaimAsString(TokenService.CLAIM_STAFF_ID);

        return new AuthenticatedUser(
                UUID.fromString(jwt.getSubject()),
                UUID.fromString(jwt.getClaimAsString(TokenService.CLAIM_BUSINESS_ID)),
                AuthenticatedUser.Role.valueOf(jwt.getClaimAsString(TokenService.CLAIM_ROLE)),
                staffId != null ? UUID.fromString(staffId) : null);
    }
}
