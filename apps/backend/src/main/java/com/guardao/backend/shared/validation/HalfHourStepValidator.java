package com.guardao.backend.shared.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * GUA-31 — Comprobacion de {@link HalfHourStep}.
 *
 * El cero y los negativos se rechazan aqui y no con un @Min aparte, para que
 * un solo mensaje explique la regla completa: una cita de cero minutos no
 * ocupa ningun bloque de la agenda y la base la rechazaria igual.
 */
public class HalfHourStepValidator implements ConstraintValidator<HalfHourStep, Integer> {

    private static final int STEP = 30;

    @Override
    public boolean isValid(Integer minutos, ConstraintValidatorContext context) {
        // De que venga se encarga @NotNull; ver el javadoc de la anotacion
        if (minutos == null) {
            return true;
        }

        return minutos > 0 && minutos % STEP == 0;
    }
}
