package com.guardao.backend.schedule;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * GUA-35 — El calculo de huecos, y nada mas.
 *
 * No conoce la base de datos, ni los repositorios, ni el reloj: recibe los
 * ratos abiertos, los ratos ocupados y cuanto dura el servicio, y devuelve a
 * que horas se puede empezar. Esta aparte a proposito.
 *
 * La razon es que este es el codigo que hay que poder probar barato y a
 * fondo. El ticket lo dice: es el corazon del producto, de aqui sale lo que ve
 * el cliente al reservar. Mezclado con las consultas, cada caso limite
 * —jornada partida, bloqueo a mitad de franja, servicio largo al final del
 * dia— costaria levantar Postgres y sembrar cinco tablas. Como funcion pura,
 * cada uno es tres lineas (GUA-40).
 *
 * El paso es de 30 minutos y no de "la duracion del servicio". Con el paso
 * igual a la duracion, un corte de 30 despues de un tinturado de 90 solo
 * podria empezar a las 9:00 o a las 10:30, y las 10:00 —libres y utiles— no
 * se ofrecerian nunca. Con paso fijo, la rejilla es la misma para todos los
 * servicios y encaja con la de la agenda.
 */
final class AvailabilityCalculator {

    private AvailabilityCalculator() {
    }

    /**
     * Las horas a las que puede empezar un servicio de esa duracion.
     *
     * @param abiertos   ratos en los que el barbero atiende ese dia
     * @param ocupados   bloqueos y citas ya agendadas
     * @param duracion   lo que dura el servicio elegido
     * @param paso       cada cuanto se ofrece un inicio (la rejilla de la agenda)
     * @param noAntesDe  momento a partir del cual tiene sentido ofrecer; sirve
     *                   para no ofrecer horas que ya pasaron
     */
    static List<Instant> freeStarts(List<TimeRange> abiertos, List<TimeRange> ocupados,
            Duration duracion, Duration paso, Instant noAntesDe) {

        List<Instant> inicios = new ArrayList<>();

        for (TimeRange abierto : abiertos) {
            Instant inicio = abierto.startAt();

            // La condicion de corte es que quepa ENTERO, no que empiece dentro:
            // un tinturado de 90 minutos a las 17:00 con cierre a las 18:00 no
            // es un hueco, es una cita que termina con la sede cerrada
            while (!inicio.plus(duracion).isAfter(abierto.endAt())) {
                TimeRange candidato = new TimeRange(inicio, inicio.plus(duracion));

                if (!inicio.isBefore(noAntesDe) && estaLibre(candidato, ocupados)) {
                    inicios.add(inicio);
                }

                inicio = inicio.plus(paso);
            }
        }

        return inicios.stream().distinct().sorted().toList();
    }

    /**
     * Cruza los ratos abiertos de la sede con los del barbero.
     *
     * Devuelve los de la sede tal cual cuando el barbero no declaro horario
     * propio, que es el caso normal: la mayoria de los barberos trabaja el
     * horario del local y no hay por que obligar a copiarlo.
     */
    static List<TimeRange> restrictTo(List<TimeRange> deLaSede, List<TimeRange> delBarbero,
            boolean elBarberoTieneHorarioPropio) {

        if (!elBarberoTieneHorarioPropio) {
            return deLaSede;
        }

        List<TimeRange> comunes = new ArrayList<>();
        for (TimeRange sede : deLaSede) {
            for (TimeRange barbero : delBarbero) {
                TimeRange comun = sede.intersection(barbero);
                if (comun != null) {
                    comunes.add(comun);
                }
            }
        }

        return comunes;
    }

    private static boolean estaLibre(TimeRange candidato, List<TimeRange> ocupados) {
        for (TimeRange ocupado : ocupados) {
            if (candidato.overlaps(ocupado)) {
                return false;
            }
        }

        return true;
    }
}
