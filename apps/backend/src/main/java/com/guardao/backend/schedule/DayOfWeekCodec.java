package com.guardao.backend.schedule;

import java.time.DayOfWeek;

/**
 * GUA-33 — Traduce entre el dia de la semana de Java y el que guarda la base.
 *
 * Son dos numeraciones distintas y ninguna es "la buena":
 *
 * - java.time.DayOfWeek va de 1 (lunes) a 7 (domingo), el estandar ISO
 * - la columna schedule.day_of_week va de 0 (domingo) a 6 (sabado), que es lo
 *   que espera un calendario y lo que declara la migracion inicial
 *
 * Coinciden de lunes a sabado y solo se separan en el domingo, que es
 * exactamente lo que vuelve peligrosa la conversion a mano: sale bien en las
 * pruebas de un martes cualquiera y corre el horario un dia entero cuando
 * alguien reserva un domingo.
 *
 * Por eso la conversion vive aqui y en ningun otro sitio.
 */
final class DayOfWeekCodec {

    private DayOfWeekCodec() {
    }

    /** De java.time (1 lunes .. 7 domingo) al de la base (0 domingo .. 6 sabado). */
    static short toDatabase(DayOfWeek dia) {
        // El resto de 7 solo mueve el domingo: 7 % 7 = 0. El resto queda igual
        return (short) (dia.getValue() % 7);
    }

    /** Del de la base (0 domingo .. 6 sabado) al de java.time. */
    static DayOfWeek fromDatabase(short dia) {
        if (dia < 0 || dia > 6) {
            throw new IllegalArgumentException("Dia de la semana fuera de rango: " + dia);
        }

        return dia == 0 ? DayOfWeek.SUNDAY : DayOfWeek.of(dia);
    }
}
