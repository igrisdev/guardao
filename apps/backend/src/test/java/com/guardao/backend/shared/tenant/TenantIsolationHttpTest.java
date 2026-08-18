package com.guardao.backend.shared.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.guardao.backend.business.Location;
import com.guardao.backend.business.LocationRepository;
import com.guardao.backend.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * GUA-22 — El aislamiento sobre una peticion HTTP de verdad.
 *
 * TenantIsolationTest prueba el filtro llamando a los repositorios; aqui se
 * recorre la cadena completa tal como ocurre en produccion: token, filtro que
 * resuelve el negocio, controlador y consulta.
 *
 * Esa diferencia importa. En este camino no hay ninguna transaccion abierta
 * por fuera, asi que la abre el propio repositorio; es el escenario donde el
 * orden entre el interceptor de transacciones y el aspecto tiene que ser el
 * correcto (ver TenancyConfig). Si ese orden se rompiera, este test cae y el
 * de repositorios podria seguir pasando.
 *
 * El controlador de sonda existe solo aqui: sirve para observar el
 * aislamiento sin adelantar endpoints que son de otros tickets (GUA-25).
 */
@AutoConfigureMockMvc
@Import(TenantIsolationHttpTest.ControladorDeSonda.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TenantIsolationHttpTest extends IntegrationTest {

    private static final String RUTA_SEDES = "/api/v1/test/sedes";
    private static final String RUTA_NEGOCIO = "/api/v1/test/negocio-actual";

    @Autowired
    private MockMvc mockMvc;

    private String tokenPropio;
    private String negocioPropioId;
    private UUID sedeAjena;
    private UUID sedePropia;

    @BeforeAll
    void registrarDosBarberias() throws Exception {
        String propio = registrar("http-propia", "http-propia@elcorte.co");
        String ajeno = registrar("http-ajena", "http-ajena@elcorte.co");

        tokenPropio = JsonPath.read(propio, "$.accessToken");
        negocioPropioId = JsonPath.read(propio, "$.businessId");

        // Los identificadores de sede se toman sin negocio resuelto, que es
        // como los veria alguien que ya los conoce por otra via
        sedePropia = sedeDe(UUID.fromString(negocioPropioId));
        sedeAjena = sedeDe(UUID.fromString(JsonPath.read(ajeno, "$.businessId")));
    }

    @Autowired
    private LocationRepository locations;

    private UUID sedeDe(UUID businessId) {
        TenantContext.clear();
        return locations.findByBusinessId(businessId).getFirst().getId();
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

    @Test
    @DisplayName("el negocio del token queda resuelto durante la peticion")
    void elNegocioDelTokenQuedaResuelto() throws Exception {
        mockMvc.perform(get(RUTA_NEGOCIO)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenPropio))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(negocioPropioId));
    }

    @Test
    @DisplayName("al listar sedes con su token solo ve la suya")
    void alListarSedesSoloVeLaSuya() throws Exception {
        String json = mockMvc.perform(get(RUTA_SEDES)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenPropio))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<String> sedes = JsonPath.read(json, "$");

        assertThat(sedes)
                .as("hay al menos dos barberias registradas y solo debe ver la propia")
                .containsExactly(sedePropia.toString());
    }

    @Test
    @DisplayName("pedir la sede de otra barberia por su id responde 404")
    void pedirLaSedeAjenaResponde404() throws Exception {
        // Con su propia sede si responde, para descartar que el 404 venga de
        // que el endpoint este roto
        mockMvc.perform(get(RUTA_SEDES + "/" + sedePropia)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenPropio))
                .andExpect(status().isOk());

        mockMvc.perform(get(RUTA_SEDES + "/" + sedeAjena)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenPropio))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("sin token no se llega siquiera a consultar")
    void sinTokenNoSeLlegaAConsultar() throws Exception {
        mockMvc.perform(get(RUTA_SEDES))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("el negocio no queda pegado al hilo despues de responder")
    void elNegocioNoQuedaPegadoAlHilo() throws Exception {
        mockMvc.perform(get(RUTA_NEGOCIO)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenPropio))
                .andExpect(status().isOk());

        // Los hilos se reutilizan entre peticiones: si el contexto no se
        // limpiara, la siguiente peticion heredaria este negocio
        assertThat(TenantContext.current())
                .as("el filtro debe limpiar el contexto al terminar")
                .isEmpty();
    }

    /**
     * Endpoints de observacion, solo para este test. No forman parte de la
     * aplicacion: leen a traves de los mismos repositorios que usaria
     * cualquier endpoint real, que es lo que se quiere comprobar.
     */
    @TestConfiguration
    static class ControladorDeSonda {

        @RestController
        @RequestMapping("/api/v1/test")
        static class Sonda {

            private final LocationRepository locations;

            Sonda(LocationRepository locations) {
                this.locations = locations;
            }

            @GetMapping("/negocio-actual")
            String negocioActual() {
                return TenantContext.current().map(UUID::toString).orElse("sin-negocio");
            }

            @GetMapping("/sedes")
            List<String> listarSedes() {
                return locations.findAll().stream()
                        .map(Location::getId)
                        .map(UUID::toString)
                        .toList();
            }

            @GetMapping("/sedes/{id}")
            ResponseEntity<String> sedePorId(@PathVariable UUID id) {
                return locations.findById(id)
                        .map(sede -> ResponseEntity.ok(sede.getName()))
                        .orElseGet(() -> ResponseEntity.notFound().build());
            }
        }
    }
}
