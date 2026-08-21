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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * GUA-31 — CRUD de barberos sobre HTTP.
 *
 * El ticket pide poder crear, editar y eliminar barberos de una sede. Se
 * comprueba eso y, como en las sedes, que nada de otra barberia sea
 * alcanzable: por eso hay dos negocios registrados y varios casos que
 * apuntan a la sede del otro.
 */
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StaffCrudTest extends IntegrationTest {

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
        String propio = registrar("barberos-propia", "barberos-propia@elcorte.co");
        tokenPropio = JsonPath.read(propio, "$.accessToken");
        sedePropia = primeraSede(tokenPropio);

        String tokenAjeno = JsonPath.read(
                registrar("barberos-ajena", "barberos-ajena@elcorte.co"), "$.accessToken");
        sedeAjena = primeraSede(tokenAjeno);

        // Token de un barbero del mismo negocio, emitido a mano: crear
        // usuarios STAFF de verdad es GUA-37, y aqui solo hace falta el rol
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

    /** La sede que le creo el registro a esa barberia. */
    private UUID primeraSede(String token) throws Exception {
        String sedes = mockMvc.perform(get("/api/v1/locations")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return UUID.fromString(JsonPath.read(sedes, "$[0].id"));
    }

    private String ruta(UUID locationId) {
        return "/api/v1/locations/" + locationId + "/staff";
    }

    private String crearBarbero(String nombre) throws Exception {
        return mockMvc.perform(post(ruta(sedePropia))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenPropio)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "%s"}
                        """.formatted(nombre)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    @DisplayName("crea un barbero en la sede y queda activo desde el principio")
    void creaUnBarberoYQuedaActivo() throws Exception {
        String json = crearBarbero("Andres Mesa");

        assertThat(JsonPath.<String>read(json, "$.name")).isEqualTo("Andres Mesa");
        assertThat(JsonPath.<Boolean>read(json, "$.active")).isTrue();
        assertThat(JsonPath.<String>read(json, "$.locationId"))
                .isEqualTo(sedePropia.toString());

        String id = JsonPath.read(json, "$.id");
        mockMvc.perform(get(ruta(sedePropia) + "/" + id)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenPropio))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Andres Mesa"));
    }

    @Test
    @DisplayName("edita el nombre de un barbero")
    void editaElNombre() throws Exception {
        String id = JsonPath.read(crearBarbero("Nombre Mal Escrito"), "$.id");

        mockMvc.perform(put(ruta(sedePropia) + "/" + id)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenPropio)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "Nombre Corregido"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Nombre Corregido"));
    }

    @Test
    @DisplayName("dar de baja a un barbero lo desactiva, no lo borra")
    void darDeBajaLoDesactiva() throws Exception {
        String id = JsonPath.read(crearBarbero("Barbero Que Se Va"), "$.id");

        mockMvc.perform(delete(ruta(sedePropia) + "/" + id)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenPropio))
                .andExpect(status().isNoContent());

        // Sigue existiendo, con su historial intacto, pero marcado como inactivo
        mockMvc.perform(get(ruta(sedePropia) + "/" + id)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenPropio))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        // Y deja de aparecer cuando se piden solo los que siguen atendiendo
        String activos = mockMvc.perform(get(ruta(sedePropia) + "?activos=true")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenPropio))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<List<String>>read(activos, "$[*].id")).doesNotContain(id);
    }

    @Test
    @DisplayName("un barbero dado de baja se puede volver a activar")
    void unBarberoDadoDeBajaSePuedeReactivar() throws Exception {
        String id = JsonPath.read(crearBarbero("Barbero Temporal"), "$.id");

        mockMvc.perform(delete(ruta(sedePropia) + "/" + id)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenPropio))
                .andExpect(status().isNoContent());

        mockMvc.perform(post(ruta(sedePropia) + "/" + id + "/reactivate")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenPropio))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    @DisplayName("no se puede crear ni listar barberos en la sede de otra barberia")
    void noSePuedeTocarLaSedeAjena() throws Exception {
        mockMvc.perform(get(ruta(sedeAjena))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenPropio))
                .andExpect(status().isNotFound());

        mockMvc.perform(post(ruta(sedeAjena))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenPropio)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "Infiltrado"}
                        """))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("un barbero de otra barberia no se puede consultar ni editar")
    void unBarberoAjenoNoEsAlcanzable() throws Exception {
        // Se crea con el token del otro negocio, en su propia sede
        String tokenAjeno = JsonPath.read(
                registrar("barberos-tercera", "barberos-tercera@elcorte.co"), "$.accessToken");
        UUID sedeTercera = primeraSede(tokenAjeno);

        String ajeno = mockMvc.perform(post(ruta(sedeTercera))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAjeno)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "Barbero De Otro"}
                        """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String idAjeno = JsonPath.read(ajeno, "$.id");

        // Ni por su propia sede (que no es del negocio del token)...
        mockMvc.perform(get(ruta(sedeTercera) + "/" + idAjeno)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenPropio))
                .andExpect(status().isNotFound());

        // ...ni colgandolo de una sede propia para saltarse la comprobacion
        mockMvc.perform(put(ruta(sedePropia) + "/" + idAjeno)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenPropio)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "Secuestrado"}
                        """))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("sin token no se puede ni listar")
    void sinTokenNoSePuedeNiListar() throws Exception {
        mockMvc.perform(get(ruta(sedePropia)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("un barbero sin nombre no pasa la validacion")
    void unBarberoSinNombreNoPasaLaValidacion() throws Exception {
        mockMvc.perform(post(ruta(sedePropia))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenPropio)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "   "}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details.fields.name").exists());
    }

    @Test
    @DisplayName("un barbero puede ver el equipo de la sede pero no modificarlo")
    void unBarberoConsultaPeroNoConfigura() throws Exception {
        mockMvc.perform(get(ruta(sedePropia))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenBarbero))
                .andExpect(status().isOk());

        mockMvc.perform(post(ruta(sedePropia))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenBarbero)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "Contratado Por El Mismo"}
                        """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
}
