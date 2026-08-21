package com.guardao.backend.schedule;

import static com.guardao.backend.support.EscenarioDeBarberia.franja;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.guardao.backend.support.EscenarioDeBarberia;
import com.guardao.backend.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

/**
 * GUA-40 — El endpoint de disponibilidad de punta a punta (GUA-35).
 *
 * AvailabilityCalculatorTest ya cubre la cuenta caso por caso. Lo que se
 * comprueba aqui es lo otro: que las cinco piezas lleguen bien —horario de la
 * sede, horario del barbero, bloqueos, habilidades y la duracion del
 * servicio—, que la hora local de la sede se convierta al instante correcto, y
 * que el aislamiento entre barberias siga en pie.
 *
 * Todo ocurre en un sabado dentro de un mes. Sabado porque es el dia con
 * jornada partida, y dentro de un mes porque el motor no ofrece horas que ya
 * pasaron: con la fecha de hoy, la mitad de los asertos dependerian de la hora
 * a la que corra la suite.
 */
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AvailabilityEndpointTest extends IntegrationTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final int SABADO = 6;

    /** Un sabado con mas de un mes por delante. */
    private static final LocalDate DIA = LocalDate.now(BOGOTA)
            .plusDays(30)
            .with(TemporalAdjusters.next(DayOfWeek.SATURDAY));

    @Autowired
    private MockMvc mockMvc;

    private EscenarioDeBarberia propia;
    private EscenarioDeBarberia ajena;

    @BeforeAll
    void prepararLaSedeConJornadaPartida() throws Exception {
        propia = new EscenarioDeBarberia(mockMvc, "dispo-propia", "dispo-propia@elcorte.co");
        ajena = new EscenarioDeBarberia(mockMvc, "dispo-ajena", "dispo-ajena@elcorte.co");

        // El sabado se abre de 8 a 12 y de 2 a 6, que es la jornada partida
        // tipica de una barberia
        propia.horarioDeLaSede(
                franja(SABADO, "08:00", "12:00") + "," + franja(SABADO, "14:00", "18:00"));
    }

    /** Las horas de inicio ofrecidas ese dia, en hora local de la sede. */
    private List<String> huecos(UUID serviceId) throws Exception {
        String json = mockMvc.perform(get(propia.ruta("/availability"))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + propia.token())
                .param("serviceId", serviceId.toString())
                .param("from", DIA.toString())
                .param("to", DIA.toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<String> instantes = JsonPath.read(json, "$.days[0].slots[*].startAt");

        return instantes.stream()
                .map(texto -> Instant.parse(texto).atZone(BOGOTA).toLocalTime())
                .map(LocalTime::toString)
                .toList();
    }

    /** Un barbero nuevo con la habilidad de ese servicio ya asignada. */
    private UUID barberoQueSabe(String nombre, UUID serviceId) throws Exception {
        UUID barbero = propia.crearBarbero(nombre);
        propia.asignarHabilidad(barbero, serviceId);
        return barbero;
    }

    @Test
    @DisplayName("solo se ofrecen horas dentro del horario de la sede")
    void soloSeOfrecenHorasDentroDelHorario() throws Exception {
        UUID corte = propia.crearServicio("Corte de una hora", 25000, 60);
        barberoQueSabe("Solo horario", corte);

        List<String> resultado = huecos(corte);

        assertThat(resultado)
                .startsWith("08:00")
                .endsWith("17:00")
                // El almuerzo no se ofrece, y las 11:30 tampoco: terminarian a
                // las 12:30, con la sede cerrada
                .doesNotContain("11:30", "12:00", "12:30", "13:00", "13:30")
                .contains("11:00", "14:00");
    }

    @Test
    @DisplayName("para un servicio de 90 minutos no se ofrece un hueco de 60")
    void paraUnServicioDeNoventaNoSeOfreceUnHuecoDeSesenta() throws Exception {
        // Es el "listo cuando" textual del ticket. El ultimo hueco de la mañana
        // es a las 10:30, porque a las 11:00 el servicio terminaria a las 12:30
        UUID tinturado = propia.crearServicio("Tinturado", 80000, 90);
        barberoQueSabe("Tinturados", tinturado);

        assertThat(huecos(tinturado))
                .contains("10:30", "16:30")
                .doesNotContain("11:00", "17:00");
    }

    @Test
    @DisplayName("un bloqueo quita las horas que tapa, y solo esas")
    void unBloqueoQuitaLasHorasQueTapa() throws Exception {
        UUID corte = propia.crearServicio("Corte con bloqueo", 25000, 60);
        UUID barbero = barberoQueSabe("Con cita medica", corte);

        // Cita medica de 9 a 10, hora de Bogota
        propia.bloquear(barbero,
                DIA.atTime(9, 0).atZone(BOGOTA).toInstant().toString(),
                DIA.atTime(10, 0).atZone(BOGOTA).toInstant().toString(),
                "Cita medica");

        assertThat(huecos(corte))
                // Las 8:30 terminarian a las 9:30, en plena cita
                .doesNotContain("08:30", "09:00", "09:30")
                // Tocarse no es solaparse: a las 10:00 ya esta libre
                .contains("08:00", "10:00");
    }

    @Test
    @DisplayName("el horario propio del barbero recorta el de la sede")
    void elHorarioPropioRecorta() throws Exception {
        UUID corte = propia.crearServicio("Corte de media jornada", 25000, 60);
        UUID barbero = barberoQueSabe("Media jornada", corte);

        // Solo trabaja las mañanas del sabado
        propia.horarioDelBarbero(barbero, franja(SABADO, "08:00", "12:00"));

        assertThat(huecos(corte))
                .contains("08:00", "11:00")
                .doesNotContain("14:00", "17:00");
    }

    @Test
    @DisplayName("el horario del barbero no lo saca del de la sede")
    void elHorarioDelBarberoNoLoSacaDelDeLaSede() throws Exception {
        UUID corte = propia.crearServicio("Corte de madrugador", 25000, 60);
        UUID barbero = barberoQueSabe("Madrugador", corte);

        // Declara de 6 a 20, pero la sede abre de 8 a 12 y de 14 a 18
        propia.horarioDelBarbero(barbero, franja(SABADO, "06:00", "20:00"));

        assertThat(huecos(corte))
                .startsWith("08:00")
                .endsWith("17:00")
                .doesNotContain("06:00", "07:00", "18:00", "19:00");
    }

    @Test
    @DisplayName("un barbero sin la habilidad no aparece, por libre que este")
    void unBarberoSinLaHabilidadNoAparece() throws Exception {
        UUID tinturado = propia.crearServicio("Tinturado exclusivo", 90000, 60);
        propia.crearBarbero("No sabe tinturar");

        // Nadie tiene la habilidad asignada: no hay nada que ofrecer, y no es
        // un error sino una barberia a la que le falta configurar quien hace que
        assertThat(huecos(tinturado)).isEmpty();
    }

    @Test
    @DisplayName("un barbero dado de baja deja de ofrecer horas")
    void unBarberoDadoDeBajaNoOfrece() throws Exception {
        UUID corte = propia.crearServicio("Corte del que se fue", 25000, 60);
        UUID barbero = barberoQueSabe("Se va", corte);

        assertThat(huecos(corte)).isNotEmpty();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .delete(propia.ruta("/staff/" + barbero))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + propia.token()))
                .andExpect(status().isNoContent());

        assertThat(huecos(corte)).isEmpty();
    }

    @Test
    @DisplayName("los dias sin horario vienen en la respuesta, con la lista vacia")
    void losDiasSinHorarioVienenVacios() throws Exception {
        UUID corte = propia.crearServicio("Corte de domingo", 25000, 60);
        barberoQueSabe("Domingos", corte);

        // El domingo siguiente al sabado configurado: la sede no abre
        LocalDate domingo = DIA.plusDays(1);

        mockMvc.perform(get(propia.ruta("/availability"))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + propia.token())
                .param("serviceId", corte.toString())
                .param("from", DIA.toString())
                .param("to", domingo.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days.length()").value(2))
                .andExpect(jsonPath("$.days[1].date").value(domingo.toString()))
                .andExpect(jsonPath("$.days[1].slots").isEmpty());
    }

    @Test
    @DisplayName("se puede pedir la disponibilidad de un solo barbero")
    void sePuedePedirLaDeUnSoloBarbero() throws Exception {
        UUID corte = propia.crearServicio("Corte de dos barberos", 25000, 60);
        UUID uno = barberoQueSabe("Barbero uno", corte);
        barberoQueSabe("Barbero dos", corte);

        String json = mockMvc.perform(get(propia.ruta("/availability"))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + propia.token())
                .param("serviceId", corte.toString())
                .param("from", DIA.toString())
                .param("to", DIA.toString())
                .param("staffId", uno.toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<List<String>>read(json, "$.days[0].slots[*].staffId"))
                .isNotEmpty()
                .containsOnly(uno.toString());
    }

    @Test
    @DisplayName("la respuesta repite el servicio y su duracion")
    void laRespuestaRepiteElServicio() throws Exception {
        UUID corte = propia.crearServicio("Corte con datos", 25000, 60);
        barberoQueSabe("Con datos", corte);

        mockMvc.perform(get(propia.ruta("/availability"))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + propia.token())
                .param("serviceId", corte.toString())
                .param("from", DIA.toString())
                .param("to", DIA.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceName").value("Corte con datos"))
                .andExpect(jsonPath("$.durationMin").value(60));
    }

    @Test
    @DisplayName("un rango al reves se rechaza")
    void unRangoAlReves() throws Exception {
        UUID corte = propia.crearServicio("Corte de rango", 25000, 60);

        mockMvc.perform(get(propia.ruta("/availability"))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + propia.token())
                .param("serviceId", corte.toString())
                .param("from", DIA.toString())
                .param("to", DIA.minusDays(1).toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TIME_RANGE"));
    }

    @Test
    @DisplayName("un rango de mas de dos meses se rechaza")
    void unRangoDemasiadoLargo() throws Exception {
        UUID corte = propia.crearServicio("Corte de rango largo", 25000, 60);

        mockMvc.perform(get(propia.ruta("/availability"))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + propia.token())
                .param("serviceId", corte.toString())
                .param("from", DIA.toString())
                .param("to", DIA.plusYears(1).toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("no se puede consultar la disponibilidad de otra barberia")
    void noSePuedeConsultarLaDeOtraBarberia() throws Exception {
        UUID servicioAjeno = ajena.crearServicio("Corte de otra", 25000, 60);

        mockMvc.perform(get(ajena.ruta("/availability"))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + propia.token())
                .param("serviceId", servicioAjeno.toString())
                .param("from", DIA.toString())
                .param("to", DIA.toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("sin token no se puede consultar")
    void sinTokenNoSePuedeConsultar() throws Exception {
        UUID corte = propia.crearServicio("Corte sin token", 25000, 60);

        mockMvc.perform(get(propia.ruta("/availability"))
                .param("serviceId", corte.toString())
                .param("from", DIA.toString())
                .param("to", DIA.toString()))
                .andExpect(status().isUnauthorized());
    }
}
