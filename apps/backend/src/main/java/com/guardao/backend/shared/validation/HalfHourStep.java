package com.guardao.backend.shared.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * GUA-31 — El valor en minutos debe ser un multiplo positivo de 30.
 *
 * Existe como anotacion propia y no como un @Min suelto porque la regla no es
 * "que sea grande" sino "que encaje en la rejilla": la agenda se dibuja en
 * bloques de media hora y el motor de disponibilidad (GUA-35) recorre esos
 * mismos bloques. Un servicio de 45 minutos deja medio bloque muerto que
 * ningun otro puede ocupar.
 *
 * La base de datos ya lo exige con service_duration_half_hour, y ahi seguira
 * como ultima defensa. Lo que aporta esta validacion es el mensaje: sin ella,
 * escribir 45 en el formulario devuelve un 409 generico de integridad en vez
 * de decir cual campo esta mal y por que.
 *
 * Un valor nulo se da por valido: de exigirlo se encarga @NotNull, para que
 * un campo vacio no reporte dos errores distintos del mismo problema.
 */
@Documented
@Constraint(validatedBy = HalfHourStepValidator.class)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER,
        ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface HalfHourStep {

    String message() default "La duracion debe ir en pasos de 30 minutos (30, 60, 90...)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
