package com.guardao.backend.schedule;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * GUA-34 — Datos de un bloqueo al crearlo o editarlo.
 *
 * Las fechas viajan como instantes ISO con zona ("2026-09-01T13:00:00Z" o
 * "2026-09-01T08:00:00-05:00"), no como hora local suelta. Un bloqueo es un
 * momento concreto en la linea del tiempo, a diferencia del horario semanal,
 * que si es hora local de la sede.
 *
 * No incluye el barbero: va en la URL, y de ahi se verifica contra la sede y
 * el negocio del token (ADR-004).
 */
public record BlockRequest(

        @NotNull(message = "La fecha de inicio es obligatoria")
        Instant startAt,

        @NotNull(message = "La fecha de fin es obligatoria")
        Instant endAt,

        @Size(max = 200, message = "El motivo no puede pasar de 200 caracteres")
        String reason) {
}
