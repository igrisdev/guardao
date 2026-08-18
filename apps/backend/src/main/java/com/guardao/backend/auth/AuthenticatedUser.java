package com.guardao.backend.auth;

import java.util.UUID;

/**
 * GUA-11 — Contexto del usuario que hace la peticion, tomado del JWT.
 *
 * El businessId sale SIEMPRE de aqui, nunca de un parametro de la peticion.
 * Aceptarlo del cliente seria permitir que cualquiera lea datos de otro
 * negocio (ADR-004). El filtrado automatico por businessId es GUA-22.
 *
 * @param staffId solo tiene valor cuando el rol es STAFF; es lo que permite
 *                validar que un barbero solo complete sus propias citas.
 */
public record AuthenticatedUser(
        UUID userId,
        UUID businessId,
        Role role,
        UUID staffId) {

    public enum Role {
        OWNER,
        STAFF,
        SUPER_ADMIN
    }

    public boolean isOwner() {
        return role == Role.OWNER;
    }

    public boolean isStaff() {
        return role == Role.STAFF;
    }
}
