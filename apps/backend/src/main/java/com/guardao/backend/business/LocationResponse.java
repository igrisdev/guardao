package com.guardao.backend.business;

import java.time.Instant;
import java.util.UUID;

/**
 * GUA-25 — Sede tal como la ve el dashboard.
 *
 * No expone el businessId: quien consulta ya sabe de que negocio es, porque
 * solo puede ver el suyo. Devolverlo no aporta y acostumbra al frontend a
 * manejar un dato que nunca deberia mandar de vuelta.
 */
public record LocationResponse(
        UUID id,
        String name,
        String address,
        String city,
        boolean active,
        Instant createdAt) {

    static LocationResponse from(Location location) {
        return new LocationResponse(
                location.getId(),
                location.getName(),
                location.getAddress(),
                location.getCity(),
                location.isActive(),
                location.getCreatedAt());
    }
}
