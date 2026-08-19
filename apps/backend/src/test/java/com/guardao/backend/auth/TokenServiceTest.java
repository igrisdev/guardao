package com.guardao.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.guardao.backend.support.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * GUA-20 — Emision de tokens para los dos tipos de usuario.
 *
 * El caso del dueño esta aqui por una razon concreta: el token se armaba
 * poniendo staff_id en nulo, y JwtClaimsSet rechaza los valores nulos, asi
 * que ningun OWNER podia iniciar sesion. No se habia visto porque hasta el
 * registro no existia ningun endpoint que emitiera un token.
 */
class TokenServiceTest extends IntegrationTest {

    @Autowired
    private TokenService tokenService;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Test
    @DisplayName("el dueño recibe un token valido, sin el claim de barbero")
    void duenoRecibeTokenSinClaimDeBarbero() {
        AuthenticatedUser dueno = new AuthenticatedUser(
                UUID.randomUUID(), UUID.randomUUID(), AuthenticatedUser.Role.OWNER, null);

        Jwt token = jwtDecoder.decode(tokenService.createAccessToken(dueno));

        assertThat(token.getClaimAsString(TokenService.CLAIM_STAFF_ID))
                .as("el claim se omite, no viaja vacio")
                .isNull();
        assertThat(token.getClaimAsString(TokenService.CLAIM_ROLE)).isEqualTo("OWNER");

        // Y al leerlo de vuelta se reconstruye el mismo usuario
        assertThat(CurrentUser.fromJwt(token)).isEqualTo(dueno);
    }

    @Test
    @DisplayName("el barbero lleva su staff_id en el token")
    void barberoLlevaSuStaffIdEnElToken() {
        AuthenticatedUser barbero = new AuthenticatedUser(
                UUID.randomUUID(), UUID.randomUUID(), AuthenticatedUser.Role.STAFF, UUID.randomUUID());

        Jwt token = jwtDecoder.decode(tokenService.createAccessToken(barbero));

        assertThat(token.getClaimAsString(TokenService.CLAIM_STAFF_ID))
                .isEqualTo(barbero.staffId().toString());
        assertThat(CurrentUser.fromJwt(token)).isEqualTo(barbero);
    }

    @Test
    @DisplayName("el token de refresco no sirve para llamar a la API")
    void tokenDeRefrescoNoSirveParaLlamarLaApi() {
        Jwt refresco = jwtDecoder.decode(tokenService.createRefreshToken(UUID.randomUUID()));

        assertThat(TokenService.isAccessToken(refresco)).isFalse();
    }
}
