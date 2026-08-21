package com.guardao.backend.staff;

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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * GUA-31 — CRUD de servicios sobre HTTP.
 *
 * El criterio de aceptacion del ticket tiene dos partes: que el CRUD funcione
 * y que la duracion solo acepte multiplos de 30. La segunda es la que mas
 * casos tiene, porque es la que sostiene la rejilla de la agenda (GUA-35).
 */
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ServiceCrudTest extends IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TokenService tokenService;

    private String tokenPropio;
    private String tokenBarbero;
    private UUID sedePropia;
    private UUID sedeAjena;

    @BeforeAll
    void registrarDosBarberias() throws Exception {
        String propio = registrar("servicios-propia", "servicios-propia@elcorte.co");
        tokenPropio = JsonPath.read(propio, "$.accessToken");
        sedePropia = primeraSede(tokenPropio);

        String tokenAjeno = JsonPath.read(
                registrar("servicios-ajena", "servicios-ajena@elcorte.co"), "$.accessToken");
        sedeAjena = primeraSede(tokenAjeno);

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

    private UUID primeraSede(String token) throws Exception {
        String sedes = mockMvc.perform(get("/api/v1/locations")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return UUID.fromString(JsonPath.read(sedes, "$[0].id"));
    }

    private String ruta(UUID locationId) {
        return "/api/v1/locations/" + locationId + "/services";
    }

    private String cuerpo(String nombre, int precio, Integer duracion) {
        return """
                {"name": "%s", "price": %d, "durationMin": %s}
                """.formatted(nombre, precio, duracion);
    }

    private String crearServicio(String nombre, int precio, int duracion) throws Exception {
        return mockMvc.perform(post(ruta(sedePropia))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenPropio)
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo(nombre, precio, duracion)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    @DisplayName("crea un servicio con su precio y su duracion")
    void creaUnServicio() throws Exception {
        String json = crearServicio("Corte clasico", 25000, 30);

        assertThat(JsonPath.<String>read(json, "$.name")).isEqualTo("Corte clasico");
        assertThat(JsonPath.<Integer>read(json, "$.price")).isEqualTo(25000);
        assertThat(JsonPath.<Integer>read(json, "$.durationMin")).isEqualTo(30);
        assertThat(JsonPath.<Boolean>read(json, "$.active")).isTrue();

        String id = JsonPath.read(json, "$.id");
        mockMvc.perform(get(ruta(sedePropia) + "/" + id)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenPropio))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(25000));
    }

    @Test
    @DisplayName("un tinturado dura mas que un corte, y ambos son multiplos de 30")
    void serviciosDeDistintaDuracion() throws Exception {
        assertThat(JsonPath.<Integer>read(
                crearServicio("Barba", 12000, 30), "$.durationMin")).isEqualTo(30);
        assertThat(JsonPath.<Integer>read(
                crearServicio("Tinturado", 80000, 120), "$.durationMin")).isEqualTo(120);
    }

    @ParameterizedTest(name = "una duracion de {0} minutos se rechaza")
    @ValueSource(ints = {45, 1, 29, 31, 89, 0, -30})
    @DisplayName("la duracion solo acepta multiplos de 30")
    void laDuracionSoloAceptaMultiplosDe30(int duracion) throws Exception {
        mockMvc.perform(post(ruta(sedePropia))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenPropio)
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo("Duracion invalida", 20000, duracion)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                // El frontend marca el campo en rojo con esto, no con el texto
                .andExpect(jsonPath("$.details.fields.durationMin").exists());
    }

    @Test
    @DisplayName("editar tampoco deja poner una duracion suelta")
    void editarTampocoAceptaDuracionSuelta() throws Exception {
        String id = JsonPath.read(crearServicio("Corte con maquina", 20000, 30), "$.id");

        mockMvc.perform(put(ruta(sedePropia) + "/" + id)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenPropio)
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo("Corte con maquina", 20000, 45)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.durationMin").exists());
    }

    @Test
    @DisplayName("sin duracion o sin precio no se crea")
    void sinDuracionOSinPrecioNoSeCrea() throws Exception {
        mockMvc.perform(post(ruta(sedePropia))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenPropio)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "Sin duracion", "price": 20000}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.durationMin").exists());

        mockMvc.perform(post(ruta(sedePropia))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenPropio)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "Sin precio", "durationMin": 30}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.price").exists());
    }

    @Test
    @DisplayName("un precio negativo se rechaza")
    void unPrecioNegativoSeRechaza() throws Exception {
        mockMvc.perform(post(ruta(sedePropia))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenPropio)
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo("Precio raro", -1000, 30)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.price").exists());
    }

    @Test
    @DisplayName("un servicio gratis si se acepta: hay barberias que regalan la barba")
    void unServicioGratisSeAcepta() throws Exception {
        assertThat(JsonPath.<Integer>read(
                crearServicio("Arreglo de cejas", 0, 30), "$.price")).isZero();
    }

    @Test
    @DisplayName("edita el nombre, el precio y la duracion de un servicio")
    void editaElServicio() throws Exception {
        String id = JsonPath.read(crearServicio("Corte basico", 20000, 30), "$.id");

        mockMvc.perform(put(ruta(sedePropia) + "/" + id)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenPropio)
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo("Corte premium", 35000, 60)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Corte premium"))
                .andExpect(jsonPath("$.price").value(35000))
                .andExpect(jsonPath("$.durationMin").value(60));
    }

    @Test
    @DisplayName("retirar un servicio lo desactiva, no lo borra")
    void retirarLoDesactiva() throws Exception {
        String id = JsonPath.read(crearServicio("Servicio Que Se Retira", 15000, 30), "$.id");

        mockMvc.perform(delete(ruta(sedePropia) + "/" + id)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenPropio))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(ruta(sedePropia) + "/" + id)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenPropio))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        String activos = mockMvc.perform(get(ruta(sedePropia) + "?activos=true")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenPropio))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<List<String>>read(activos, "$[*].id")).doesNotContain(id);
    }

    @Test
    @DisplayName("un servicio retirado se puede volver a ofrecer")
    void unServicioRetiradoSePuedeReactivar() throws Exception {
        String id = JsonPath.read(crearServicio("Servicio Temporal", 15000, 30), "$.id");

        mockMvc.perform(delete(ruta(sedePropia) + "/" + id)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenPropio))
                .andExpect(status().isNoContent());

        mockMvc.perform(post(ruta(sedePropia) + "/" + id + "/reactivate")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenPropio))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    @DisplayName("no se puede listar ni crear servicios en la sede de otra barberia")
    void noSePuedeTocarLaSedeAjena() throws Exception {
        mockMvc.perform(get(ruta(sedeAjena))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenPropio))
                .andExpect(status().isNotFound());

        mockMvc.perform(post(ruta(sedeAjena))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenPropio)
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo("Servicio Infiltrado", 10000, 30)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("sin token no se puede ni listar")
    void sinTokenNoSePuedeNiListar() throws Exception {
        mockMvc.perform(get(ruta(sedePropia)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("un barbero puede consultar el catalogo pero no cambiarlo")
    void unBarberoConsultaPeroNoConfigura() throws Exception {
        // Necesita el precio y la duracion para agendar desde el mostrador
        mockMvc.perform(get(ruta(sedePropia))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenBarbero))
                .andExpect(status().isOk());

        mockMvc.perform(post(ruta(sedePropia))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenBarbero)
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo("Servicio Del Barbero", 10000, 30)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
}
