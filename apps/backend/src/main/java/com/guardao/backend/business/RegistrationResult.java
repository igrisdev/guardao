package com.guardao.backend.business;

import java.util.UUID;

/**
 * GUA-20 — Lo que queda creado tras un registro.
 *
 * Devuelve identificadores y no las entidades: quien llama (el modulo auth)
 * solo necesita saber a quien acaba de crear para emitir su sesion, no
 * manipular objetos persistentes de otro modulo (ADR-002).
 */
public record RegistrationResult(
        UUID businessId,
        String slug,
        UUID locationId,
        UUID ownerId,
        UserRole role) {
}
