package com.guardao.backend.schedule;

import java.util.List;
import java.util.UUID;

/**
 * GUA-35 — Respuesta del endpoint de disponibilidad.
 *
 * Repite el servicio y su duracion aunque el que pregunta ya los sabia: es lo
 * que permite pintar "Tinturado - 90 min" encima del calendario sin una
 * segunda peticion, y deja claro con que duracion se calcularon estos huecos
 * si alguien la cambia mientras el cliente elige.
 */
public record AvailabilityResponse(
        UUID locationId,
        UUID serviceId,
        String serviceName,
        int durationMin,
        List<AvailabilityDayResponse> days) {
}
