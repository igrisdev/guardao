package com.guardao.backend.schedule;

import com.guardao.backend.shared.validation.HalfHourClock;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;

/**
 * GUA-33 — Una franja del horario semanal.
 *
 * dayOfWeek va de 0 (domingo) a 6 (sabado). Es la numeracion de la base y,
 * de paso, la misma que devuelve Date.getDay() en el navegador, asi que el
 * formulario no tiene que convertir nada.
 *
 * Las horas viajan como "HH:mm" (formato ISO de LocalTime) y sin zona: son la
 * hora local de la sede, no un instante.
 */
public record ScheduleSlotRequest(

        @NotNull(message = "El dia de la semana es obligatorio")
        @Min(value = 0, message = "El dia de la semana va de 0 (domingo) a 6 (sabado)")
        @Max(value = 6, message = "El dia de la semana va de 0 (domingo) a 6 (sabado)")
        Integer dayOfWeek,

        @NotNull(message = "La hora de apertura es obligatoria")
        @HalfHourClock
        LocalTime openTime,

        @NotNull(message = "La hora de cierre es obligatoria")
        @HalfHourClock
        LocalTime closeTime) {
}
