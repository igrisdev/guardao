package com.guardao.backend.business;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.guardao.backend.auth.AuthenticatedUser;
import com.guardao.backend.auth.TokenService;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * GUA-25 — CRUD de sedes sobre HTTP.
 *
 * El ticket pide dos cosas: que el CRUD funcione y que respete el aislamiento
 * por negocio. Por eso hay dos barberias registradas y varios casos que
 * intentan alcanzar la sede de la otra.
 */
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LocationCrudTest extends IntegrationTest {

    private static final String RUTA = "/api/v1/locations";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TokenService tokenService;

    private String tokenPropio;
    private String tokenBarbero;
    private String sedeAjena;

    @BeforeAll
    void registrarDosBarberias() throws Exception {
        String propio = registrar("sedes-propia", "sedes-propia@elcorte.co");
        tokenPropio = JsonPath.read(propio, "$.accessToken");

        String tokenAjeno = JsonPath.read(
                registrar("sedes-ajena", "sedes-ajena@elcorte.co"), "$.accessToken");

        // La sede que creo el registro de la otra barberia
        String suyas = mockMvc.perform(get(RUTA)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAjeno))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        sedeAjena = JsonPath.read(suyas, "$[0].id");

        // Token de un barbero de la misma barberia. Se emite a mano porque
        // crear usuarios STAFF necesita la entidad Staff, que es de la Etapa 2
        // (GUA-23); el token basta, porque los permisos se resuelven con lo
        // que trae dentro
        UUID negocioPropio = UUID.fromString(JsonPath.read(propio, "$.businessId"));
        tokenBarbero = tokenService.createAccessToken(new AuthenticatedUser(
                UUID.randomUUID(), negocioPropio, AuthenticatedUser.Role.STAFF, UUID.randomUUID()));
    }

    private String registrar(String slug, String correo) throws Exception {
        String cuerpo = """
                {
                  "businessName": "Barberia %s",
                  "slug": "%s",
                  "locationName": "Sede Original",
                  "email": "%s",
                  "password": "clave-segura-123"
                }
                """.formatted(slug, slug, correo);

        return mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private String crearSede(String nombre) throws Exception {
        String cuerpo = """
                {"name": "%s", "address": "Calle 10 # 5-20", "city": "Cali"}
                """.formatted(nombre);

        return mockMvc.perform(post(RUTA)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenPropio)
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    @DisplayName("crea una sede y queda activa desde el principio")
    void creaUnaSedeYQuedaActiva() throws Exception {
        String json = crearSede("Sede Norte");

        assertThat(JsonPath.<String>read(json, "$.name"))
                .isEqualTo("Sede Norte");
        assertThat(JsonPath.<Boolean>read(json, "$.active"))
                .isTrue();

        String id = JsonPath.read(json, "$.id");
        mockMvc.perform(get(RUTA + "/" + id)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenPropio))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.city").value("Cali"));
    }

    @Test
    @DisplayName("edita el nombre y la direccion de una sede")
    void editaLaSede() throws Exception {
        String id = JsonPath.read(crearSede("Sede Con Error"), "$.id");

        mockMvc.perform(put(RUTA + "/" + id)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenPropio)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "Sede Corregida", "address": "Carrera 5 # 8-15", "city": "Palmira"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Sede Corregida"))
                .andExpect(jsonPath("$.city").value("Palmira"));
    }

    @Test
    @DisplayName("cerrar una sede la desactiva, no la borra")
    void cerrarUnaSedeLaDesactiva() throws Exception {
        String id = JsonPath.read(crearSede("Sede Que Cierra"), "$.id");

        mockMvc.perform(delete(RUTA + "/" + id)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenPropio))
                .andExpect(status().isNoContent());

        // Sigue existiendo, con su historial intacto, pero marcada como cerrada
        mockMvc.perform(get(RUTA + "/" + id)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenPropio))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        // Y deja de aparecer cuando se piden solo las abiertas
        String activas = mockMvc.perform(get(RUTA + "?activas=true")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenPropio))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<List<String>>read(activas, "$[*].id"))
                .doesNotContain(id);
    }

    @Test
    @DisplayName("una sede cerrada se puede volver a abrir")
    void unaSedeCerradaSePuedeVolverAAbrir() throws Exception {
        String id = JsonPath.read(crearSede("Sede Temporal"), "$.id");

        mockMvc.perform(delete(RUTA + "/" + id)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenPropio))
                .andExpect(status().isNoContent());

        mockMvc.perform(post(RUTA + "/" + id + "/reactivate")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenPropio))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    @DisplayName("no deja cerrar la unica sede activa del negocio")
    void noDejaCerrarLaUnicaSedeActiva() throws Exception {
        // Esta barberia se queda solo con la sede que le creo el registro
        String token = JsonPath.read(
                registrar("sedes-unica", "sedes-unica@elcorte.co"), "$.accessToken");

        String suyas = mockMvc.perform(get(RUTA)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String unica = JsonPath.read(suyas, "$[0].id");

        mockMvc.perform(delete(RUTA + "/" + unica)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LAST_ACTIVE_LOCATION"));
    }

    @Test
    @DisplayName("al listar no aparece ninguna sede de otra barberia")
    void alListarNoApareceNingunaSedeAjena() throws Exception {
        String json = mockMvc.perform(get(RUTA)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenPropio))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<List<String>>read(json, "$[*].id"))
                .doesNotContain(sedeAjena);
    }

    @Test
    @DisplayName("consultar la sede de otra barberia responde 404")
    void consultarLaSedeAjenaResponde404() throws Exception {
        mockMvc.perform(get(RUTA + "/" + sedeAjena)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenPropio))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("tampoco se puede editar ni cerrar la sede de otra barberia")
    void tampocoSePuedeTocarLaSedeAjena() throws Exception {
        mockMvc.perform(put(RUTA + "/" + sedeAjena)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenPropio)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "Secuestrada", "address": null, "city": null}
                        """))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete(RUTA + "/" + sedeAjena)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenPropio))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("sin token no se puede ni listar")
    void sinTokenNoSePuedeNiListar() throws Exception {
        mockMvc.perform(get(RUTA))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("una sede sin nombre no pasa la validacion")
    void unaSedeSinNombreNoPasaLaValidacion() throws Exception {
        mockMvc.perform(post(RUTA)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenPropio)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "  ", "address": "Calle 1", "city": "Cali"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("un barbero puede consultar las sedes pero no configurarlas")
    void unBarberoConsultaPeroNoConfigura() throws Exception {
        // Ver en que sedes trabaja si le corresponde
        mockMvc.perform(get(RUTA)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenBarbero))
                .andExpect(status().isOk());

        // Abrir, editar o cerrar sedes es del dueño
        mockMvc.perform(post(RUTA)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenBarbero)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "Sede Del Barbero", "address": null, "city": null}
                        """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
}
