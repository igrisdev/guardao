package com.guardao.backend.schedule;

import java.time.LocalDate;
import java.util.List;

/**
 * GUA-35 — Los huecos de un dia.
 *
 * Los dias sin ningun hueco se devuelven igual, con la lista vacia. Omitirlos
 * obligaria al calendario a distinguir "ese dia no vino en la respuesta" de
 * "ese dia esta lleno", y son la misma cosa para quien mira: un dia sin nada
 * que ofrecer.
 */
public record AvailabilityDayResponse(
        LocalDate date,
        List<AvailableSlotResponse> slots) {
}
