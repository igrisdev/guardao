package com.guardao.backend.staff;

import java.time.Instant;
import java.util.UUID;

/**
 * GUA-31 — Servicio tal como lo ve el dashboard.
 *
 * price y durationMin salen tal cual estan hoy. Quien los lea para mostrar
 * una cita ya agendada esta leyendo el dato equivocado: esa cita guarda su
 * propia copia de ambos (ADR-010).
 */
public record ServiceResponse(
        UUID id,
        UUID locationId,
        String name,
        int price,
        int durationMin,
        boolean active,
        Instant createdAt) {

    static ServiceResponse from(Service servicio) {
        return new ServiceResponse(
                servicio.getId(),
                servicio.getLocationId(),
                servicio.getName(),
                servicio.getPrice(),
                servicio.getDurationMin(),
                servicio.isActive(),
                servicio.getCreatedAt());
    }
}
