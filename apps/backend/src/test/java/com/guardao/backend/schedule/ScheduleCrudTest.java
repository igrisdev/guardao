package com.guardao.backend.schedule;

import static com.guardao.backend.support.EscenarioDeBarberia.franja;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.guardao.backend.support.EscenarioDeBarberia;
import com.guardao.backend.support.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * GUA-40 — Horarios de sede y de barbero sobre HTTP (GUA-33).
 *
 * El "listo cuando" del ticket es concreto: poder definir el horario de una
 * sede con jornada partida y el horario propio de un barbero. Los dos casos
 * estan abajo, y con ellos las reglas que impiden guardar un horario que
 * despues rompa el calculo de disponibilidad.
 *
 * El dia 6 es el sabado (0 = domingo), que es justo el que suele tener jornada
 * partida en una barberia.
 */
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ScheduleCrudTest extends IntegrationTest {

    private static final int SABADO = 6;
    private static final int LUNES = 1;

    @Autowired
    private MockMvc mockMvc;

    private EscenarioDeBarberia propia;
    private EscenarioDeBarberia ajena;
    private UUID barbero;

    @BeforeAll
    void prepararBarberias() throws Exception {
        propia = new EscenarioDeBarberia(mockMvc, "horarios-propia", "horarios-propia@elcorte.co");
        ajena = new EscenarioDeBarberia(mockMvc, "horarios-ajena", "horarios-ajena@elcorte.co");
        barbero = propia.crearBarbero("Andres Mesa");
    }

    private org.springframework.test.web.servlet.ResultActions guardarSede(String franjas)
            throws Exception {
        return mockMvc.perform(put(propia.ruta("/schedule"))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + propia.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"slots\": [%s]}".formatted(franjas)));
    }

    @Test
    @DisplayName("un sabado con jornada partida se guarda como dos franjas del mismo dia")
    void unSabadoConJornadaPartida() throws Exception {
        guardarSede(franja(SABADO, "08:00", "12:00") + "," + franja(SABADO, "14:00", "18:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                // Vuelven ordenadas por dia y hora, que es como las pinta el
                // formulario
                .andExpect(jsonPath("$[0].openTime").value("08:00:00"))
                .andExpect(jsonPath("$[1].openTime").value("14:00:00"));
    }

    @Test
    @DisplayName("guardar reemplaza la semana entera, no acumula franjas")
    void guardarReemplazaLaSemanaEntera() throws Exception {
        guardarSede(franja(LUNES, "08:00", "18:00")).andExpect(status().isOk());
        guardarSede(franja(LUNES, "09:00", "17:00")).andExpect(status().isOk());

        mockMvc.perform(get(propia.ruta("/schedule"))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + propia.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].openTime").value("09:00:00"));
    }

    @Test
    @DisplayName("una semana vacia cierra la sede todos los dias")
    void unaSemanaVaciaCierraLaSede() throws Exception {
        guardarSede(franja(LUNES, "08:00", "18:00")).andExpect(status().isOk());

        guardarSede("").andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @DisplayName("dos franjas del mismo dia que se cruzan se rechazan")
    void dosFranjasDelMismoDiaQueSeCruzan() throws Exception {
        guardarSede(franja(SABADO, "08:00", "13:00") + "," + franja(SABADO, "12:00", "18:00"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OVERLAPPING_SCHEDULE"));
    }

    @Test
    @DisplayName("dos franjas que solo se tocan si se aceptan")
    void dosFranjasQueSoloSeTocan() throws Exception {
        // De 8 a 12 y de 12 a 18 es una jornada seguida partida en dos filas.
        // El formulario puede producirla sin querer y no rompe nada
        guardarSede(franja(SABADO, "08:00", "12:00") + "," + franja(SABADO, "12:00", "18:00"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("franjas de dias distintos a la misma hora no se cruzan")
    void franjasDeDiasDistintosNoSeCruzan() throws Exception {
        guardarSede(franja(LUNES, "08:00", "18:00") + "," + franja(SABADO, "08:00", "18:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("una franja que cierra antes de abrir se rechaza")
    void unaFranjaQueCierraAntesDeAbrir() throws Exception {
        guardarSede(franja(LUNES, "18:00", "08:00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TIME_RANGE"));
    }

    @Test
    @DisplayName("una hora fuera de la rejilla de media hora se rechaza")
    void unaHoraFueraDeLaRejilla() throws Exception {
        guardarSede(franja(LUNES, "08:15", "18:00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("un dia de la semana fuera de 0..6 se rechaza")
    void unDiaFueraDeRango() throws Exception {
        guardarSede(franja(7, "08:00", "18:00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("un barbero sin horario propio devuelve vacio, que significa que sigue el de la sede")
    void unBarberoSinHorarioPropio() throws Exception {
        UUID nuevo = propia.crearBarbero("Sin horario propio");

        mockMvc.perform(get(propia.ruta("/staff/" + nuevo + "/schedule"))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + propia.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @DisplayName("el horario propio de un barbero se guarda aparte del de la sede")
    void elHorarioDelBarberoEsAparte() throws Exception {
        guardarSede(franja(LUNES, "08:00", "18:00")).andExpect(status().isOk());

        mockMvc.perform(put(propia.ruta("/staff/" + barbero + "/schedule"))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + propia.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"slots\": [%s]}".formatted(franja(LUNES, "08:00", "12:00"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].staffId").value(barbero.toString()));

        // El de la sede sigue intacto: son dos horarios, no uno que se pisa
        mockMvc.perform(get(propia.ruta("/schedule"))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + propia.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].staffId").doesNotExist())
                .andExpect(jsonPath("$[0].closeTime").value("18:00:00"));
    }

    @Test
    @DisplayName("quitar el horario propio devuelve al barbero al de la sede")
    void quitarElHorarioPropio() throws Exception {
        UUID otro = propia.crearBarbero("Vuelve al de la sede");
        propia.horarioDelBarbero(otro, franja(LUNES, "08:00", "12:00"));

        mockMvc.perform(delete(propia.ruta("/staff/" + otro + "/schedule"))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + propia.token()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(propia.ruta("/staff/" + otro + "/schedule"))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + propia.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @DisplayName("no se puede ver ni cambiar el horario de otra barberia")
    void noSePuedeTocarElHorarioAjeno() throws Exception {
        ajena.horarioDeLaSede(franja(LUNES, "08:00", "18:00"));

        mockMvc.perform(get(ajena.ruta("/schedule"))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + propia.token()))
                .andExpect(status().isNotFound());

        mockMvc.perform(put(ajena.ruta("/schedule"))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + propia.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"slots\": [%s]}".formatted(franja(LUNES, "00:00", "23:30"))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("sin token no se puede consultar el horario")
    void sinTokenNoSePuedeConsultar() throws Exception {
        mockMvc.perform(get(propia.ruta("/schedule")))
                .andExpect(status().isUnauthorized());
    }
}
