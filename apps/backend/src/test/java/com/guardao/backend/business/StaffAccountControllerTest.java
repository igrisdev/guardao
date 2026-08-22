package com.guardao.backend.business;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.guardao.backend.auth.AuthenticatedUser;
import com.guardao.backend.auth.TokenService;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * GUA-23 — El dueño crea el acceso de un barbero, sobre HTTP.
 *
 * Los registros de Staff se insertan por SQL: la entidad y su CRUD son de
 * otro ticket (GUA-31). Aqui basta con que la fila exista, que es lo que la
 * llave foranea de app_user.staff_id exige.
 *
 * Se registran dos barberias para probar el aislamiento: el dueño de una no
 * puede crearle el acceso a un barbero de la otra.
 */
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StaffAccountControllerTest extends IntegrationTest {

    private static final String RUTA = "/api/v1/staff-accounts";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private JwtDecoder jwtDecoder;

    private String tokenPropio;
    private String tokenBarbero;
    private UUID sedePropia;
    private UUID sedeAjena;

    @BeforeAll
    void registrarDosBarberias() throws Exception {
        String propio = registrar("staff-propia", "staff-propia@elcorte.co");
        tokenPropio = JsonPath.read(propio, "$.accessToken");
        sedePropia = primeraSede(tokenPropio);

        String ajeno = registrar("staff-ajena", "staff-ajena@elcorte.co");
        sedeAjena = primeraSede(JsonPath.read(ajeno, "$.accessToken"));

        // Token de un barbero de la barberia propia, emitido a mano: crear el
        // usuario STAFF es justo lo que este ticket construye, asi que aqui
        // solo hace falta el token para probar que un barbero no puede crear
        // accesos
        UUID negocioPropio = UUID.fromString(JsonPath.read(propio, "$.businessId"));
        tokenBarbero = tokenService.createAccessToken(new AuthenticatedUser(
                UUID.randomUUID(), negocioPropio, AuthenticatedUser.Role.STAFF, UUID.randomUUID()));
    }

    private String registrar(String slug, String correo) throws Exception {
        String cuerpo = """
                {
                  "businessName": "Barberia %s",
                  "slug": "%s",
                  "locationName": "Sede de %s",
                  "email": "%s",
                  "password": "clave-segura-123"
                }
                """.formatted(slug, slug, slug, correo);

        return mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private UUID primeraSede(String token) throws Exception {
        String json = mockMvc.perform(get("/api/v1/locations")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return UUID.fromString(JsonPath.read(json, "$[0].id"));
    }

    /**
     * Inserta un barbero a mano en una sede. La entidad Staff es de la Etapa 2
     * (GUA-31); la llave foranea de app_user.staff_id ya exige que la fila
     * exista.
     */
    private UUID insertarStaff(UUID sedeId, String nombre) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO staff (id, location_id, name) VALUES (?, ?, ?)", id, sedeId, nombre);
        return id;
    }

    private String cuerpo(UUID staffId, String correo) {
        return """
                {"staffId": "%s", "email": "%s", "password": "clave-barbero-123"}
                """.formatted(staffId, correo);
    }

    private String crearAcceso(UUID staffId, String correo) throws Exception {
        return mockMvc.perform(post(RUTA)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenPropio)
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo(staffId, correo)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    @DisplayName("el dueño crea el acceso de un barbero y ese barbero inicia sesion")
    void creaElAccesoYElBarberoIniciaSesion() throws Exception {
        UUID staffId = insertarStaff(sedePropia, "Pedro");

        String creado = crearAcceso(staffId, "pedro@elcorte.co");

        // El acceso queda atado al barbero y con rol STAFF
        assertThat(JsonPath.<String>read(creado, "$.role")).isEqualTo("STAFF");
        assertThat(JsonPath.<String>read(creado, "$.staffId")).isEqualTo(staffId.toString());

        // La prueba de fondo (el "listo cuando"): con ese correo y clave el
        // barbero de verdad inicia sesion
        String sesion = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "pedro@elcorte.co", "password": "clave-barbero-123"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("STAFF"))
                .andReturn().getResponse().getContentAsString();

        // Y su token lleva el barbero, que es lo que despues ata la cita a quien
        // la atiende
        Jwt token = jwtDecoder.decode(JsonPath.read(sesion, "$.accessToken"));
        assertThat(token.getClaimAsString(TokenService.CLAIM_STAFF_ID))
                .isEqualTo(staffId.toString());
    }

    @Test
    @DisplayName("no se puede crear el acceso de un barbero de otra barberia")
    void noSePuedeVincularUnBarberoAjeno() throws Exception {
        UUID staffAjeno = insertarStaff(sedeAjena, "Barbero de la otra");

        mockMvc.perform(post(RUTA)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenPropio)
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo(staffAjeno, "colado@elcorte.co")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STAFF_NOT_FOUND"));
    }

    @Test
    @DisplayName("un barbero que no existe responde igual que uno ajeno")
    void barberoInexistenteResponde404() throws Exception {
        mockMvc.perform(post(RUTA)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenPropio)
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo(UUID.randomUUID(), "fantasma@elcorte.co")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STAFF_NOT_FOUND"));
    }

    @Test
    @DisplayName("un barbero no puede tener dos accesos")
    void unBarberoNoSePuedeDuplicar() throws Exception {
        UUID staffId = insertarStaff(sedePropia, "Juan");

        crearAcceso(staffId, "juan@elcorte.co");

        mockMvc.perform(post(RUTA)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenPropio)
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo(staffId, "juan-otro@elcorte.co")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STAFF_ALREADY_HAS_LOGIN"));
    }

    @Test
    @DisplayName("no se puede reutilizar un correo que ya tiene cuenta")
    void noSePuedeReutilizarUnCorreo() throws Exception {
        UUID uno = insertarStaff(sedePropia, "Barbero Uno");
        UUID otro = insertarStaff(sedePropia, "Barbero Dos");

        crearAcceso(uno, "repetido@elcorte.co");

        mockMvc.perform(post(RUTA)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenPropio)
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo(otro, "repetido@elcorte.co")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
    }

    @Test
    @DisplayName("una contraseña corta no pasa la validacion")
    void datosInvalidosNoPasan() throws Exception {
        UUID staffId = insertarStaff(sedePropia, "Corta");

        mockMvc.perform(post(RUTA)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenPropio)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"staffId": "%s", "email": "corta@elcorte.co", "password": "1234"}
                        """.formatted(staffId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("un barbero no puede crear accesos, solo el dueño")
    void unBarberoNoPuedeCrearAccesos() throws Exception {
        UUID staffId = insertarStaff(sedePropia, "Quiere Mandar");

        mockMvc.perform(post(RUTA)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenBarbero)
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo(staffId, "mandar@elcorte.co")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("sin token no se puede crear un acceso")
    void sinTokenNoSePuedeCrear() throws Exception {
        mockMvc.perform(post(RUTA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo(UUID.randomUUID(), "sin-token@elcorte.co")))
                .andExpect(status().isUnauthorized());
    }
}
