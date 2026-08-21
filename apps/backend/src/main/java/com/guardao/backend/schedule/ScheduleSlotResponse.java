package com.guardao.backend.schedule;

import java.time.LocalTime;
import java.util.UUID;

/**
 * GUA-33 — Una franja de horario tal como la ve el dashboard.
 *
 * Lleva staffId para que una sola forma sirva a los dos horarios: nulo es la
 * franja general de la sede, con valor es la de ese barbero. Sin el campo, la
 * interfaz tendria que acordarse de cual de los dos endpoints pidio para
 * saber que esta mostrando.
 */
public record ScheduleSlotResponse(
        UUID id,
        UUID locationId,
        UUID staffId,
        int dayOfWeek,
        LocalTime openTime,
        LocalTime closeTime) {

    static ScheduleSlotResponse from(Schedule franja) {
        return new ScheduleSlotResponse(
                franja.getId(),
                franja.getLocationId(),
                franja.getStaffId(),
                franja.getDayOfWeek(),
                franja.getOpenTime(),
                franja.getCloseTime());
    }
}
