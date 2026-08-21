package com.guardao.backend.staff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.guardao.backend.auth.AuthenticatedUser;
import com.guardao.backend.auth.TokenService;
import com.guardao.backend.support.EscenarioDeBarberia;
import com.guardao.backend.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
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
 * GUA-40 — Asignacion de habilidades sobre HTTP (GUA-32).
 *
 * El criterio de aceptacion de GUA-32 pide tres cosas: asignar, revocar y
 * poder consultar que barberos atienden un servicio dado. Las tres estan
 * abajo, mas los dos limites que importan: que la operacion sea idempotente
 * —la pantalla marca y desmarca casillas— y que no se pueda cruzar un barbero
 * con un servicio de otra sede.
 */
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SkillCrudTest extends IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TokenService tokenService;

    private EscenarioDeBarberia propia;
    private EscenarioDeBarberia ajena;
    private String tokenBarbero;

    private UUID barbero;
    private UUID corte;
    private UUID tinturado;

    @BeforeAll
    void prepararDosBarberias() throws Exception {
        propia = new EscenarioDeBarberia(mockMvc, "skills-propia", "skills-propia@elcorte.co");
        ajena = new EscenarioDeBarberia(mockMvc, "skills-ajena", "skills-ajena@elcorte.co");

        barbero = propia.crearBarbero("Andres Mesa");
        corte = propia.crearServicio("Corte clasico", 25000, 30);
        tinturado = propia.crearServicio("Tinturado", 80000, 90);

        tokenBarbero = tokenService.createAccessToken(new AuthenticatedUser(
                UUID.randomUUID(), propia.businessId(),
                AuthenticatedUser.Role.STAFF, UUID.randomUUID()));
    }

    private String habilidad(UUID staffId, UUID serviceId) {
        return propia.ruta("/staff/" + staffId + "/skills/" + serviceId);
    }

    @Test
    @DisplayName("asignar una habilidad la deja consultable desde los dos lados")
    void asignarDejaLaHabilidadConsultable() throws Exception {
        mockMvc.perform(put(habilidad(barbero, corte))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + propia.token()))
                .andExpect(status().isNoContent());

        // Que sabe hacer el barbero
        mockMvc.perform(get(propia.ruta("/staff/" + barbero + "/skills"))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + propia.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(corte.toString()));

        // Y quien atiende el servicio, que es lo que pide el ticket
        mockMvc.perform(get(propia.ruta("/services/" + corte + "/staff"))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + propia.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(barbero.toString()));
    }

    @Test
    @DisplayName("asignar dos veces no duplica ni falla")
    void asignarDosVecesEsIdempotente() throws Exception {
        UUID otro = propia.crearBarbero("Idempotente");

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(put(habilidad(otro, tinturado))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + propia.token()))
                    .andExpect(status().isNoContent());
        }

        String json = mockMvc.perform(get(propia.ruta("/staff/" + otro + "/skills"))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + propia.token()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<List<String>>read(json, "$[*].id")).hasSize(1);
    }

    @Test
    @DisplayName("revocar la quita, y revocar de nuevo tampoco falla")
    void revocarTambienEsIdempotente() throws Exception {
        UUID otro = propia.crearBarbero("Revocable");
        propia.asignarHabilidad(otro, corte);

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(delete(habilidad(otro, corte))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + propia.token()))
                    .andExpect(status().isNoContent());
        }

        mockMvc.perform(get(propia.ruta("/staff/" + otro + "/skills"))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + propia.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @DisplayName("un barbero sin habilidades devuelve lista vacia, no error")
    void unBarberoSinHabilidades() throws Exception {
        UUID nuevo = propia.crearBarbero("Recien llegado");

        mockMvc.perform(get(propia.ruta("/staff/" + nuevo + "/skills"))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + propia.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @DisplayName("no se puede cruzar un barbero con el servicio de otra sede")
    void noSePuedeCruzarConOtraSede() throws Exception {
        UUID servicioAjeno = ajena.crearServicio("Corte de otra barberia", 20000, 30);

        // El servicio existe, pero no en esta sede: se responde 404 igual que
        // si no existiera, para no delatar que ese identificador es de alguien
        mockMvc.perform(put(habilidad(barbero, servicioAjeno))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + propia.token()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("no se puede asignar en la sede de otra barberia")
    void noSePuedeAsignarEnSedeAjena() throws Exception {
        UUID barberoAjeno = ajena.crearBarbero("Barbero de otra");
        UUID servicioAjeno = ajena.crearServicio("Servicio de otra", 20000, 30);

        mockMvc.perform(put(ajena.ruta("/staff/" + barberoAjeno + "/skills/" + servicioAjeno))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + propia.token()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("un barbero puede consultar habilidades pero no cambiarlas")
    void unBarberoConsultaPeroNoConfigura() throws Exception {
        mockMvc.perform(get(propia.ruta("/services/" + corte + "/staff"))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenBarbero))
                .andExpect(status().isOk());

        mockMvc.perform(put(habilidad(barbero, tinturado))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenBarbero))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("sin token no se puede consultar")
    void sinTokenNoSePuedeConsultar() throws Exception {
        mockMvc.perform(get(propia.ruta("/staff/" + barbero + "/skills")))
                .andExpect(status().isUnauthorized());
    }
}
