package com.guardao.backend.auth;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/**
 * GUA-11 — Emision de tokens.
 *
 * La validacion no vive aqui: la hace el filtro de token portador de Spring
 * Security con el JwtDecoder configurado en SecurityConfig.
 *
 * Los endpoints que usan este servicio (login, refresh) son GUA-21.
 */
@Service
public class TokenService {

    /** Distingue un token de acceso de uno de refresco. */
    public static final String CLAIM_TOKEN_TYPE = "token_type";
    public static final String CLAIM_BUSINESS_ID = "business_id";
    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_STAFF_ID = "staff_id";

    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    private static final String ISSUER = "guardao";

    private final JwtEncoder jwtEncoder;
    private final JwtProperties properties;

    public TokenService(JwtEncoder jwtEncoder, JwtProperties properties) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
    }

    /**
     * Token de acceso: lleva el contexto completo del usuario para que ninguna
     * peticion tenga que volver a la base de datos solo para saber quien pide.
     */
    public String createAccessToken(AuthenticatedUser user) {
        Instant now = Instant.now();

        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .issuedAt(now)
                .expiresAt(now.plus(properties.accessTokenMinutes(), ChronoUnit.MINUTES))
                .subject(user.userId().toString())
                .claim(CLAIM_TOKEN_TYPE, TYPE_ACCESS)
                .claim(CLAIM_BUSINESS_ID, user.businessId().toString())
                .claim(CLAIM_ROLE, user.role().name());

        // Solo los usuarios STAFF tienen barbero asociado. El claim se omite
        // en vez de enviarse nulo: JwtClaimsSet rechaza los valores nulos, y
        // CurrentUser ya lee su ausencia como "sin barbero".
        if (user.staffId() != null) {
            claims.claim(CLAIM_STAFF_ID, user.staffId().toString());
        }

        return encode(claims.build());
    }

    /**
     * Token de refresco: deliberadamente pobre en informacion.
     *
     * Solo lleva el identificador del usuario. Al refrescar se releen rol y
     * negocio desde la base de datos, para que un cambio de rol o una baja
     * surtan efecto sin esperar a que caduque el refresco.
     */
    public String createRefreshToken(UUID userId) {
        Instant now = Instant.now();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .issuedAt(now)
                .expiresAt(now.plus(properties.refreshTokenDays(), ChronoUnit.DAYS))
                .subject(userId.toString())
                .claim(CLAIM_TOKEN_TYPE, TYPE_REFRESH)
                .build();

        return encode(claims);
    }

    /** Un token de refresco no sirve para llamar a la API. */
    public static boolean isAccessToken(Jwt jwt) {
        return TYPE_ACCESS.equals(jwt.getClaimAsString(CLAIM_TOKEN_TYPE));
    }

    private String encode(JwtClaimsSet claims) {
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
