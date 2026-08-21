package com.guardao.backend.schedule;

import java.time.Instant;

/**
 * GUA-35 — Un rato en la linea del tiempo, con inicio y fin.
 *
 * Sirve para las tres cosas que cruza el motor de disponibilidad —una franja
 * abierta, un bloqueo, una cita ya agendada— porque a la hora de calcular
 * huecos las tres son lo mismo: un rango que se solapa o no con otro. Tenerlas
 * como un solo tipo es lo que deja el calculo en una funcion corta en vez de
 * tres recorridos casi iguales.
 *
 * Los extremos se tratan como semiabiertos [inicio, fin): una cita que termina
 * a las 10:00 y otra que empieza a las 10:00 no se solapan. Es el mismo
 * criterio que usa la restriccion appointment_no_overlap con tstzrange, y
 * conviene que sea el mismo: si el calculo ofreciera un hueco que la base
 * despues rechaza, el cliente veria "ese horario ya no esta disponible" sobre
 * un horario que la pantalla le acababa de mostrar.
 */
record TimeRange(Instant startAt, Instant endAt) {

    TimeRange {
        if (!startAt.isBefore(endAt)) {
            throw new IllegalArgumentException(
                    "Un rango necesita terminar despues de empezar: " + startAt + " .. " + endAt);
        }
    }

    boolean overlaps(TimeRange otro) {
        return startAt.isBefore(otro.endAt) && endAt.isAfter(otro.startAt);
    }

    /**
     * La parte comun entre dos rangos, o null si no se tocan.
     *
     * Es lo que aplica el horario del barbero "dentro del" horario de la sede
     * (GUA-33): si el barbero declara de 7 a 19 y la sede abre de 8 a 18,
     * atiende de 8 a 18.
     */
    TimeRange intersection(TimeRange otro) {
        Instant inicio = startAt.isAfter(otro.startAt) ? startAt : otro.startAt;
        Instant fin = endAt.isBefore(otro.endAt) ? endAt : otro.endAt;

        return inicio.isBefore(fin) ? new TimeRange(inicio, fin) : null;
    }
}
