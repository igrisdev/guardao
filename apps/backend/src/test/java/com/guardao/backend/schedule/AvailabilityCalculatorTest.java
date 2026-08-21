package com.guardao.backend.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * GUA-40 — El calculo de huecos, caso por caso.
 *
 * Estos son los tests que justifican que AvailabilityCalculator sea una
 * funcion pura. No levantan Spring ni Postgres: cada caso se escribe en tres
 * lineas, corre en milisegundos, y por eso se pueden cubrir los limites raros
 * en vez de solo el camino feliz.
 *
 * Son tambien la definicion ejecutable de lo que el ticket llama "el corazon
 * del producto": si alguien cambia el motor y uno de estos se pone rojo, lo
 * que se rompio es lo que ve el cliente al reservar.
 *
 * Todas las horas son de un lunes cualquiera en Bogota. La fecha no importa
 * para el calculo —trabaja con instantes— pero escribirla en hora local hace
 * los casos legibles: "de 8 a 12" se lee mejor que un epoch.
 */
class AvailabilityCalculatorTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final LocalDate DIA = LocalDate.of(2026, 9, 7);
    private static final Duration PASO = Duration.ofMinutes(30);

    /** Un momento anterior a cualquier hora de estos tests. */
    private static final Instant SIEMPRE = Instant.EPOCH;

    private static Instant hora(String hhmm) {
        return DIA.atTime(LocalTime.parse(hhmm)).atZone(BOGOTA).toInstant();
    }

    private static TimeRange rango(String desde, String hasta) {
        return new TimeRange(hora(desde), hora(hasta));
    }

    /** Los inicios devueltos, en "HH:mm", que es como se leen los asertos. */
    private static List<String> inicios(List<Instant> instantes) {
        return instantes.stream()
                .map(instante -> instante.atZone(BOGOTA).toLocalTime().toString())
                .toList();
    }

    private static List<String> huecos(List<TimeRange> abiertos, List<TimeRange> ocupados,
            int duracionMin) {
        return inicios(AvailabilityCalculator.freeStarts(
                abiertos, ocupados, Duration.ofMinutes(duracionMin), PASO, SIEMPRE));
    }

    @Nested
    @DisplayName("Duracion del servicio")
    class DuracionDelServicio {

        @Test
        @DisplayName("una franja se parte en bloques de media hora")
        void unaFranjaSeParteEnBloquesDeMediaHora() {
            assertThat(huecos(List.of(rango("08:00", "10:00")), List.of(), 30))
                    .containsExactly("08:00", "08:30", "09:00", "09:30");
        }

        @Test
        @DisplayName("un servicio largo se ofrece cada media hora, no cada hora y media")
        void unServicioLargoSeOfreceCadaMediaHora() {
            // Con paso igual a la duracion, un tinturado de 90 solo podria
            // empezar a las 8:00 y a las 9:30, y las 8:30 —libres y utiles— no
            // se ofrecerian nunca
            assertThat(huecos(List.of(rango("08:00", "11:00")), List.of(), 90))
                    .containsExactly("08:00", "08:30", "09:00", "09:30");
        }

        @Test
        @DisplayName("un servicio largo al final del dia no se ofrece si no cabe entero")
        void unServicioLargoAlFinalDelDiaNoSeOfrece() {
            // Es el caso limite que nombra el ticket. La sede cierra a las
            // 18:00; un tinturado de 90 minutos a las 17:00 terminaria a las
            // 18:30, con el local cerrado
            assertThat(huecos(List.of(rango("16:00", "18:00")), List.of(), 90))
                    .containsExactly("16:00", "16:30");
        }

        @Test
        @DisplayName("un servicio que no cabe en ninguna franja no devuelve nada")
        void unServicioQueNoCabeNoDevuelveNada() {
            assertThat(huecos(List.of(rango("08:00", "09:00")), List.of(), 120)).isEmpty();
        }

        @Test
        @DisplayName("un servicio que ocupa la franja exacta se ofrece una sola vez")
        void unServicioQueOcupaLaFranjaExacta() {
            assertThat(huecos(List.of(rango("08:00", "09:30")), List.of(), 90))
                    .containsExactly("08:00");
        }
    }

    @Nested
    @DisplayName("Jornada partida")
    class JornadaPartida {

        private final List<TimeRange> manananYTarde =
                List.of(rango("08:00", "12:00"), rango("14:00", "18:00"));

        @Test
        @DisplayName("no se ofrece ningun hueco durante el almuerzo")
        void noSeOfreceNingunHuecoDuranteElAlmuerzo() {
            assertThat(huecos(manananYTarde, List.of(), 60))
                    .doesNotContain("12:00", "12:30", "13:00", "13:30");
        }

        @Test
        @DisplayName("un servicio no se estira de una franja a la otra")
        void unServicioNoSeEstiraDeUnaFranjaALaOtra() {
            // Las 11:30 con un servicio de 60 terminarian a las 12:30, en pleno
            // almuerzo. Que la tarde este abierta no vuelve continuo el dia
            assertThat(huecos(manananYTarde, List.of(), 60))
                    .doesNotContain("11:30")
                    .contains("11:00", "14:00");
        }

        @Test
        @DisplayName("las dos franjas del dia se ofrecen, y en orden")
        void lasDosFranjasSeOfrecenEnOrden() {
            List<String> resultado = huecos(manananYTarde, List.of(), 120);

            assertThat(resultado).containsExactly(
                    "08:00", "08:30", "09:00", "09:30", "10:00",
                    "14:00", "14:30", "15:00", "15:30", "16:00");
        }
    }

    @Nested
    @DisplayName("Bloqueos y citas ya agendadas")
    class RatosOcupados {

        @Test
        @DisplayName("un bloqueo a mitad de franja la parte en dos")
        void unBloqueoAMitadDeFranjaLaParteEnDos() {
            // El otro caso limite que nombra el ticket. La franja va de 8 a 12
            // y el barbero tiene una cita medica de 9:30 a 10:30
            List<String> resultado =
                    huecos(List.of(rango("08:00", "12:00")), List.of(rango("09:30", "10:30")), 60);

            assertThat(resultado).containsExactly("08:00", "08:30", "10:30", "11:00");
        }

        @Test
        @DisplayName("tocarse no es solaparse: un servicio puede empezar justo al terminar otro")
        void tocarseNoEsSolaparse() {
            // Mismo criterio que la restriccion appointment_no_overlap de la
            // base. Si aqui se contara como solape, el motor ofreceria menos
            // huecos de los que la base acepta y se perderian citas
            assertThat(huecos(List.of(rango("08:00", "10:00")), List.of(rango("09:00", "10:00")), 60))
                    .containsExactly("08:00");
        }

        @Test
        @DisplayName("unas vacaciones que empiezan antes y terminan despues tapan el dia entero")
        void unBloqueoQueEnvuelveElDiaLoTapaEntero() {
            assertThat(huecos(
                    List.of(rango("08:00", "18:00")),
                    List.of(new TimeRange(hora("08:00").minusSeconds(86_400),
                            hora("18:00").plusSeconds(86_400))),
                    30))
                    .isEmpty();
        }

        @Test
        @DisplayName("para un servicio de 90 no sirve un hueco de 60")
        void paraUnServicioDeNoventaNoSirveUnHuecoDeSesenta() {
            // Es literalmente el "listo cuando" de GUA-35. Entre las dos citas
            // quedan 60 minutos libres, y un tinturado de 90 no cabe ahi
            List<TimeRange> ocupados = List.of(rango("08:00", "10:00"), rango("11:00", "18:00"));

            assertThat(huecos(List.of(rango("08:00", "18:00")), ocupados, 90)).isEmpty();
            assertThat(huecos(List.of(rango("08:00", "18:00")), ocupados, 60))
                    .containsExactly("10:00");
        }
    }

    @Nested
    @DisplayName("Horario del barbero dentro del de la sede")
    class HorarioDelBarbero {

        private final List<TimeRange> sede = List.of(rango("08:00", "18:00"));

        @Test
        @DisplayName("sin horario propio, el barbero trabaja el de la sede")
        void sinHorarioPropioTrabajaElDeLaSede() {
            assertThat(AvailabilityCalculator.restrictTo(sede, List.of(), false))
                    .isEqualTo(sede);
        }

        @Test
        @DisplayName("con horario propio, vale la parte comun con el de la sede")
        void conHorarioPropioValeLaParteComun() {
            // El barbero declara de 7 a 19, pero la sede abre de 8 a 18
            List<TimeRange> resultado = AvailabilityCalculator.restrictTo(
                    sede, List.of(rango("07:00", "19:00")), true);

            assertThat(resultado).containsExactly(rango("08:00", "18:00"));
        }

        @Test
        @DisplayName("un barbero de media jornada solo atiende su mitad")
        void unBarberoDeMediaJornadaSoloAtiendeSuMitad() {
            List<TimeRange> resultado = AvailabilityCalculator.restrictTo(
                    sede, List.of(rango("08:00", "12:00")), true);

            assertThat(huecos(resultado, List.of(), 60))
                    .containsExactly("08:00", "08:30", "09:00", "09:30", "10:00", "10:30", "11:00");
        }

        @Test
        @DisplayName("un barbero que declara un dia que la sede cierra no atiende")
        void unBarberoNoAtiendeConLaSedeCerrada() {
            // La sede no abre ese dia, asi que no hay parte comun con nada
            assertThat(AvailabilityCalculator.restrictTo(
                    List.of(), List.of(rango("08:00", "12:00")), true))
                    .isEmpty();
        }

        @Test
        @DisplayName("el horario propio recorta tambien la jornada partida")
        void elHorarioPropioRecortaLaJornadaPartida() {
            List<TimeRange> resultado = AvailabilityCalculator.restrictTo(
                    List.of(rango("08:00", "12:00"), rango("14:00", "18:00")),
                    List.of(rango("10:00", "16:00")),
                    true);

            assertThat(resultado).containsExactly(rango("10:00", "12:00"), rango("14:00", "16:00"));
        }
    }

    @Nested
    @DisplayName("Horas que ya pasaron")
    class HorasQuePasaron {

        @Test
        @DisplayName("no se ofrece un hueco anterior al momento de consultar")
        void noSeOfreceUnHuecoQueYaPaso() {
            List<Instant> resultado = AvailabilityCalculator.freeStarts(
                    List.of(rango("08:00", "12:00")), List.of(),
                    Duration.ofMinutes(60), PASO, hora("10:00"));

            assertThat(inicios(resultado)).containsExactly("10:00", "10:30", "11:00");
        }
    }

    @Nested
    @DisplayName("TimeRange")
    class RangosDeTiempo {

        @Test
        @DisplayName("un rango que termina antes de empezar no se puede construir")
        void unRangoAlReves() {
            assertThatThrownBy(() -> rango("12:00", "08:00"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("dos rangos que solo se tocan no se solapan")
        void dosRangosQueSeTocan() {
            assertThat(rango("08:00", "12:00").overlaps(rango("12:00", "18:00"))).isFalse();
            assertThat(rango("08:00", "12:00").overlaps(rango("11:59", "18:00"))).isTrue();
        }

        @Test
        @DisplayName("dos rangos que no se tocan no tienen parte comun")
        void dosRangosSinParteComun() {
            assertThat(rango("08:00", "12:00").intersection(rango("14:00", "18:00"))).isNull();
        }
    }
}
