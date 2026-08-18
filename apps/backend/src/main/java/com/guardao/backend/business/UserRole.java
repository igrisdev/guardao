package com.guardao.backend.business;

/**
 * GUA-19 — Rol de un usuario dentro de un negocio.
 *
 * Los valores replican el CHECK app_user_role_valid de la migracion: si
 * aparece un rol nuevo, la base lo rechaza hasta que se migre. No cambiar
 * los nombres, que es lo que se guarda en la columna.
 *
 * El modulo auth tiene su propio AuthenticatedUser.Role para lo que viaja
 * en el JWT. Son dos cosas distintas a proposito: este describe lo que hay
 * en la tabla, y auth no depende de la capa de persistencia (ADR-002). La
 * traduccion entre ambos ocurre al iniciar sesion (GUA-20).
 */
public enum UserRole {

    /** Dueño del negocio: ve y configura todo lo suyo. */
    OWNER,

    /** Barbero con login propio; siempre va atado a un registro de staff. */
    STAFF,

    /** Personal interno de Guardao. No se crea por endpoint publico (GUA-21). */
    SUPER_ADMIN
}
