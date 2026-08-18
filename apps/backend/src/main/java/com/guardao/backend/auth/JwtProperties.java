package com.guardao.backend.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * GUA-11 — Parametros de firma y vigencia del JWT.
 *
 * Se leen de guardao.jwt.* (ver application-{perfil}.yml). El secreto nunca
 * tiene valor por defecto en staging ni en produccion: si falta la variable
 * de entorno, la aplicacion no arranca.
 */
@ConfigurationProperties(prefix = "guardao.jwt")
public record JwtProperties(
        String secret,
        int accessTokenMinutes,
        int refreshTokenDays) {

    /**
     * HS256 exige una llave de al menos 256 bits. Un secreto corto haria que
     * Nimbus fallara al construir el firmador, con un error poco claro y ya
     * en tiempo de ejecucion; mejor detenerlo aqui.
     */
    public JwtProperties {
        if (secret == null || secret.getBytes().length < 32) {
            throw new IllegalStateException(
                    "guardao.jwt.secret debe tener al menos 32 caracteres. "
                            + "Generar uno con: openssl rand -base64 48");
        }
    }
}
