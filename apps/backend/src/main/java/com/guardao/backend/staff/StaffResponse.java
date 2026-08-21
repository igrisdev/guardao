package com.guardao.backend.staff;

import java.time.Instant;
import java.util.UUID;

/**
 * GUA-31 — Barbero tal como lo ve el dashboard.
 *
 * Lleva locationId, a diferencia de LocationResponse con su negocio: la sede
 * si es un dato con el que el frontend trabaja todos los dias (el selector de
 * sede activa, GUA-36), mientras que el negocio nunca deberia viajar de
 * vuelta.
 */
public record StaffResponse(
        UUID id,
        UUID locationId,
        String name,
        boolean active,
        Instant createdAt) {

    static StaffResponse from(Staff barbero) {
        return new StaffResponse(
                barbero.getId(),
                barbero.getLocationId(),
                barbero.getName(),
                barbero.isActive(),
                barbero.getCreatedAt());
    }
}
