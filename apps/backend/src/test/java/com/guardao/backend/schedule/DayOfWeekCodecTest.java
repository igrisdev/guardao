package com.guardao.backend.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * GUA-40 — La conversion del dia de la semana.
 *
 * Parece un test de nada y es de los mas utiles de esta etapa. Java numera de
 * 1 (lunes) a 7 (domingo) y la columna schedule.day_of_week de 0 (domingo) a 6
 * (sabado): coinciden seis dias de siete. Una conversion hecha a mano funciona
 * de lunes a sabado y corre el horario un dia entero justo cuando alguien
 * reserva un domingo, sin que nada falle ni avise.
 */
class DayOfWeekCodecTest {

    @Test
    @DisplayName("el domingo es 0 en la base, no 7")
    void elDomingoEsCero() {
        assertThat(DayOfWeekCodec.toDatabase(DayOfWeek.SUNDAY)).isZero();
        assertThat(DayOfWeekCodec.fromDatabase((short) 0)).isEqualTo(DayOfWeek.SUNDAY);
    }

    @Test
    @DisplayName("de lunes a sabado los dos sistemas coinciden")
    void deLunesASabadoCoinciden() {
        assertThat(DayOfWeekCodec.toDatabase(DayOfWeek.MONDAY)).isEqualTo((short) 1);
        assertThat(DayOfWeekCodec.toDatabase(DayOfWeek.SATURDAY)).isEqualTo((short) 6);
    }

    @ParameterizedTest(name = "{0} sobrevive la ida y la vuelta")
    @EnumSource(DayOfWeek.class)
    @DisplayName("convertir y volver devuelve el mismo dia")
    void idaYVuelta(DayOfWeek dia) {
        assertThat(DayOfWeekCodec.fromDatabase(DayOfWeekCodec.toDatabase(dia))).isEqualTo(dia);
    }

    @Test
    @DisplayName("un numero fuera de 0..6 no se acepta en silencio")
    void unNumeroFueraDeRango() {
        assertThatThrownBy(() -> DayOfWeekCodec.fromDatabase((short) 7))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DayOfWeekCodec.fromDatabase((short) -1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
