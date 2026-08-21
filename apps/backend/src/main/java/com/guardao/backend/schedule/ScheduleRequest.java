package com.guardao.backend.schedule;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * GUA-33 — El horario semanal completo, tal como queda despues de guardar.
 *
 * Se manda entero y reemplaza lo que hubiera, en vez de crear y borrar
 * franjas una por una. La razon es que las reglas del horario son del
 * conjunto, no de la franja suelta: que dos franjas del sabado no se crucen
 * solo se puede comprobar mirandolas todas a la vez. Con altas y bajas
 * sueltas, guardar "de 8 a 12 y de 14 a 18" en lugar de "de 8 a 18" obliga a
 * un orden concreto de llamadas, y cualquier otro orden pasa por un estado
 * invalido o rechaza un cambio que si es valido.
 *
 * Un dia que no aparece en la lista es un dia cerrado. Mandar la lista vacia
 * cierra la sede toda la semana, y es una operacion legitima: asi se apaga un
 * horario sin borrar la sede.
 */
public record ScheduleRequest(

        @NotNull(message = "El horario es obligatorio; mande una lista vacia para cerrar todos los dias")
        // La anotacion va en el tipo de dentro y no sobre la lista: sobre la
        // lista esta deprecada en Hibernate Validator y valida el contenedor,
        // no cada franja
        List<@Valid ScheduleSlotRequest> slots) {
}
