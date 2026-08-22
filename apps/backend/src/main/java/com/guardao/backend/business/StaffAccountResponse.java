package com.guardao.backend.business;

import java.time.Instant;
import java.util.UUID;

/**
 * GUA-23 — Lo que se devuelve tras crear el acceso de un barbero.
 *
 * Nunca lleva de vuelta la contraseña ni su hash. El staffId se incluye para
 * que el dashboard confirme a que barbero quedo atado el login.
 */
public record StaffAccountResponse(
        UUID userId,
        UUID staffId,
        String email,
        UserRole role,
        boolean active,
        Instant createdAt) {

    public static StaffAccountResponse from(User user) {
        return new StaffAccountResponse(
                user.getId(),
                user.getStaffId(),
                user.getEmail(),
                user.getRole(),
                user.isActive(),
                user.getCreatedAt());
    }
}
