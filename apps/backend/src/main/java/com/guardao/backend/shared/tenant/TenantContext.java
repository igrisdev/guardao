package com.guardao.backend.shared.tenant;

import java.util.Optional;
import java.util.UUID;

/**
 * GUA-22 — Negocio al que pertenece la peticion que se esta atendiendo.
 *
 * Lo llena TenantResolutionFilter leyendo el JWT, y lo consume
 * TenantFilterActivator para restringir cada consulta. Nunca se toma de un
 * parametro de la peticion: un atacante lo cambiaria (ADR-004).
 *
 * Queda vacio a proposito en tres casos, y en todos ellos el filtro de
 * consultas queda desactivado:
 *
 * - Registro y login: todavia no se sabe de que negocio es quien escribe, y
 *   el login busca por correo en toda la plataforma
 * - Endpoints publicos de reserva: no hay JWT; el negocio se resuelve por el
 *   slug de la URL y solo se expone lo que el cliente final necesita ver
 * - Tareas programadas y arranque: no hay peticion ni usuario
 *
 * Se guarda en un ThreadLocal, asi que SIEMPRE debe limpiarse al terminar la
 * peticion: los hilos se reutilizan entre peticiones, y uno sucio le daria a
 * la siguiente el negocio de la anterior.
 */
public final class TenantContext {

    /** Nombre del filtro de Hibernate declarado en las entidades. */
    public static final String FILTER_NAME = "tenantFilter";

    /** Parametro del filtro; el nombre debe coincidir con el de la condicion SQL. */
    public static final String PARAM_BUSINESS_ID = "businessId";

    private static final ThreadLocal<UUID> ACTUAL = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(UUID businessId) {
        ACTUAL.set(businessId);
    }

    public static Optional<UUID> current() {
        return Optional.ofNullable(ACTUAL.get());
    }

    public static boolean hasTenant() {
        return ACTUAL.get() != null;
    }

    /**
     * Para el codigo que no tiene sentido sin negocio resuelto. Falla en vez
     * de devolver nulo: un nulo aqui terminaria en una consulta sin filtrar.
     */
    public static UUID require() {
        UUID businessId = ACTUAL.get();
        if (businessId == null) {
            throw new IllegalStateException(
                    "No hay negocio resuelto en esta peticion. "
                            + "Si el endpoint es publico, no debe depender del tenant.");
        }
        return businessId;
    }

    public static void clear() {
        ACTUAL.remove();
    }
}
