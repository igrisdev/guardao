package com.guardao.backend.auth;

import java.util.UUID;

/**
 * GUA-20 — Sesion recien iniciada.
 *
 * La devuelven tanto el registro como el login y el refresco (GUA-21), para
 * que el frontend tenga un unico contrato que manejar.
 *
 * No incluye la contraseña ni su hash, evidentemente, ni mas datos del
 * negocio de los justos: lo demas se pide a su propio endpoint.
 *
 * @param expiresInSeconds vigencia del token de acceso. Se envia para que el
 *                         cliente programe el refresco antes de que caduque,
 *                         en vez de descubrirlo con un 401.
 */
public record SessionResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresInSeconds,
        UUID userId,
        UUID businessId,
        String businessSlug,
        String role) {

    public static final String BEARER = "Bearer";
}
