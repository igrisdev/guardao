package com.guardao.backend.business;

import java.util.UUID;

/**
 * GUA-21 — Vista de un usuario para quien emite su sesion.
 *
 * Deliberadamente no lleva el hash de la contraseña: quien pide esto ya no
 * tiene nada que verificar, y un hash que no sale del modulo es un hash que
 * no se puede filtrar por descuido en un log o una respuesta.
 *
 * @param staffId solo tiene valor cuando el rol es STAFF.
 */
public record UserAccount(
        UUID userId,
        UUID businessId,
        String businessSlug,
        UserRole role,
        UUID staffId) {
}
