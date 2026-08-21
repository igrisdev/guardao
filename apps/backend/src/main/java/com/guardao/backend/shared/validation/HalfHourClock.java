package com.guardao.backend.shared.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * GUA-33 — La hora debe caer en punto o y media, sin segundos.
 *
 * Es la misma rejilla de media hora que ya obliga {@link HalfHourStep} para
 * la duracion de los servicios, aplicada al otro extremo: si la sede abre a
 * las 8:15 y los servicios duran multiplos de 30, los huecos del dia salen a
 * las 8:15, 8:45, 9:15... y dejan de coincidir con los de las demas franjas y
 * los de los otros barberos. La agenda deja de ser una rejilla y pasa a ser
 * varias, desalineadas entre si.
 *
 * A diferencia de la duracion, esta regla NO la respalda la base: la columna
 * es un time cualquiera. Aqui es la unica defensa, y por eso se aplica tanto
 * al abrir como al cerrar.
 *
 * Un valor nulo se da por valido; de exigirlo se encarga @NotNull.
 */
@Documented
@Constraint(validatedBy = HalfHourClockValidator.class)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER,
        ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface HalfHourClock {

    String message() default "La hora debe ir en punto o y media (08:00, 08:30, 09:00...)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
