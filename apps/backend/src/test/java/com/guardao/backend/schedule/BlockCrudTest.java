package com.guardao.backend.schedule;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.guardao.backend.support.EscenarioDeBarberia;
import com.guardao.backend.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
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
 * GUA-40 — Bloqueos de agenda sobre HTTP (GUA-34).
 *
 * Que un bloqueo desaparezca de la disponibilidad —el "listo cuando" de
 * GUA-34— se comprueba en AvailabilityEndpointTest, que es donde se puede ver
 * el efecto. Aqui va el CRUD y lo que no debe dejarse guardar.
 */
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BlockCrudTest extends IntegrationTest {

    private static final String DESDE = "2026-12-24T13:00:00Z";
    private static final String HASTA = "2026-12-26T13:00:00Z";

    @Autowired
    private MockMvc mockMvc;

    private EscenarioDeBarberia propia;
    private EscenarioDeBarberia ajena;
    private UUID barbero;

    @BeforeAll
    void prepararBarberias() throws Exception {
        propia = new EscenarioDeBarberia(mockMvc, "bloqueos-propia", "bloqueos-propia@elcorte.co");
        ajena = new EscenarioDeBarberia(mockMvc, "bloqueos-ajena", "bloqueos-ajena@elcorte.co");
        barbero = propia.crearBarbero("Andres Mesa");
    }

    private String rutaDe(UUID staffId) {
        return propia.ruta("/staff/" + staffId + "/blocks");
    }

    private String crear(UUID staffId, String desde, String hasta, String motivo) throws Exception {
        return mockMvc.perform(post(rutaDe(staffId))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + propia.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"startAt": "%s", "endAt": "%s", "reason": "%s"}
                        """.formatted(desde, hasta, motivo)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    @DisplayName("crea unas vacaciones con su motivo")
    void creaUnasVacaciones() throws Exception {
        String json = crear(barbero, DESDE, HASTA, "Vacaciones de fin de año");

        String id = JsonPath.read(json, "$.id");
        mockMvc.perform(get(rutaDe(barbero) + "/" + id)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + propia.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reason").value("Vacaciones de fin de año"))
                .andExpect(jsonPath("$.staffId").value(barbero.toString()));
    }

    @Test
    @DisplayName("un bloqueo sin motivo tambien vale")
    void unBloqueoSinMotivo() throws Exception {
        mockMvc.perform(post(rutaDe(barbero))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + propia.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"startAt": "2026-11-02T13:00:00Z", "endAt": "2026-11-02T18:00:00Z"}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reason").doesNotExist());
    }

    @Test
    @DisplayName("dos bloqueos que se solapan se aceptan")
    void dosBloqueosQueSeSolapan() throws Exception {
        UUID otro = propia.crearBarbero("Se solapa");

        crear(otro, "2026-10-05T13:00:00Z", "2026-10-10T13:00:00Z", "Vacaciones");
        // Alargar unas vacaciones creando otro bloqueo encima es normal, y el
        // barbero no queda "mas ausente" por estar bloqueado dos veces
        crear(otro, "2026-10-08T13:00:00Z", "2026-10-12T13:00:00Z", "Se alargaron");

        mockMvc.perform(get(rutaDe(otro))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + propia.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("cambiar las fechas de un bloqueo")
    void cambiarLasFechas() throws Exception {
        String id = JsonPath.read(
                crear(barbero, "2026-09-01T13:00:00Z", "2026-09-02T13:00:00Z", "Permiso"), "$.id");

        mockMvc.perform(put(rutaDe(barbero) + "/" + id)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + propia.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"startAt": "2026-09-01T13:00:00Z", "endAt": "2026-09-05T13:00:00Z",
                         "reason": "Permiso mas largo"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reason").value("Permiso mas largo"));
    }

    @Test
    @DisplayName("un bloqueo si se borra de verdad")
    void unBloqueoSeBorraDeVerdad() throws Exception {
        String id = JsonPath.read(
                crear(barbero, "2026-09-10T13:00:00Z", "2026-09-11T13:00:00Z", "Se cancela"),
                "$.id");

        mockMvc.perform(delete(rutaDe(barbero) + "/" + id)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + propia.token()))
                .andExpect(status().isNoContent());

        // A diferencia de barberos y servicios, aqui no queda nada: ese rato
        // tiene que volver a estar libre
        mockMvc.perform(get(rutaDe(barbero) + "/" + id)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + propia.token()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("un bloqueo que termina antes de empezar se rechaza")
    void unBloqueoAlReves() throws Exception {
        mockMvc.perform(post(rutaDe(barbero))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + propia.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"startAt": "2026-09-05T13:00:00Z", "endAt": "2026-09-01T13:00:00Z"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TIME_RANGE"));
    }

    @Test
    @DisplayName("un bloqueo sin fechas no pasa la validacion")
    void unBloqueoSinFechas() throws Exception {
        mockMvc.perform(post(rutaDe(barbero))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + propia.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"reason": "Sin fechas"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details.fields.startAt").exists());
    }

    @Test
    @DisplayName("no se puede bloquear al barbero de otra barberia")
    void noSePuedeBloquearAlBarberoAjeno() throws Exception {
        UUID barberoAjeno = ajena.crearBarbero("Barbero de otra");

        mockMvc.perform(post(ajena.ruta("/staff/" + barberoAjeno + "/blocks"))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + propia.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"startAt": "%s", "endAt": "%s"}
                        """.formatted(DESDE, HASTA)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("sin token no se pueden listar los bloqueos")
    void sinTokenNoSePuedeListar() throws Exception {
        mockMvc.perform(get(rutaDe(barbero)))
                .andExpect(status().isUnauthorized());
    }
}
