package com.guardao.backend.auth;

import java.util.List;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * GUA-11 — Traduce el JWT validado a una autenticacion de Spring Security.
 *
 * Hace dos cosas:
 *
 * 1. Rechaza los tokens de refresco. Sin esto, un token de refresco robado
 *    serviria para llamar a toda la API: lleva firma valida y no ha
 *    caducado, solo le faltan los claims. Se rechaza de forma explicita.
 *
 * 2. Convierte el claim "role" en la autoridad ROLE_*, que es lo que leen
 *    hasRole(...) y @PreAuthorize.
 */
public class AccessTokenAuthenticationConverter
        implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        if (!TokenService.isAccessToken(jwt)) {
            throw new InvalidBearerTokenException(
                    "Se requiere un token de acceso; se recibio uno de otro tipo");
        }

        String role = jwt.getClaimAsString(TokenService.CLAIM_ROLE);
        if (role == null) {
            throw new InvalidBearerTokenException("El token no declara rol");
        }

        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));

        return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
    }
}
