package com.guardao.backend.shared.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.time.LocalTime;

/**
 * GUA-33 — Comprobacion de {@link HalfHourClock}.
 *
 * Los segundos y los nanosegundos se revisan aparte de los minutos porque un
 * cliente puede mandar "08:00:30": los minutos serian 0 y pasaria, pero la
 * hora ya no cae en la rejilla.
 */
public class HalfHourClockValidator implements ConstraintValidator<HalfHourClock, LocalTime> {

    private static final int STEP = 30;

    @Override
    public boolean isValid(LocalTime hora, ConstraintValidatorContext context) {
        // De que venga se encarga @NotNull; ver el javadoc de la anotacion
        if (hora == null) {
            return true;
        }

        return hora.getMinute() % STEP == 0
                && hora.getSecond() == 0
                && hora.getNano() == 0;
    }
}
