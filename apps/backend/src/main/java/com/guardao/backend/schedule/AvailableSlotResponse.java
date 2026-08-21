package com.guardao.backend.schedule;

import java.time.Instant;
import java.util.UUID;

/**
 * GUA-35 — Un hueco concreto: a que hora, con que barbero y hasta cuando.
 *
 * Lleva endAt aunque se pueda deducir de startAt mas la duracion del servicio.
 * Es lo que se pinta en la agenda y en la pagina publica ("10:00 - 11:30"), y
 * recalcularlo en el navegador es repetir en JavaScript una cuenta que ya se
 * hizo bien aqui.
 *
 * staffName viaja junto al id por lo mismo: la lista de huecos se muestra
 * agrupada por barbero, y sin el nombre el frontend tendria que cruzarla
 * contra otra peticion para saber a quien corresponde cada uno.
 */
public record AvailableSlotResponse(
        Instant startAt,
        Instant endAt,
        UUID staffId,
        String staffName) {
}
