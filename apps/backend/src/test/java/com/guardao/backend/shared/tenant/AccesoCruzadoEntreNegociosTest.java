package com.guardao.backend.shared.tenant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import com.guardao.backend.auth.TokenService;
import com.guardao.backend.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;

/**
 * GUA-30 — Test de seguridad: acceso sin credencial y fuga entre negocios.
 *
 * Otros tests tocan estos temas de pasada: LocationCrudTest comprueba que la
 * sede ajena responde 404 y TokenValidationTest cubre los tokens malos. Este
 * es el test explicito que pide el ticket: el guardian que se pone en rojo si
 * alguien rompe el aislamiento, reunido en un solo lugar y sobre el endpoint
 * real de produccion.
 *
 * Aporta ademas la garantia mas fuerte, que ningun otro test hace: despues de
 * que el negocio A intenta editar y cerrar la sede del negocio B, se vuelve a
 * leer esa sede con el token de B y se comprueba que quedo intacta. No basta
 * con que el atacante reciba 404; hay que probar que el dato del otro no se
 * movio.
 */
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AccesoCruzadoEntreNegociosTest extends IntegrationTest {

    private static final String RUTA = "/api/v1/locations";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    private String tokenA;
    private String tokenB;
    private String sedeDeB;
    private String nombreOriginalDeB;

    @BeforeAll
    void registrarDosBarberias() throws Exception {
        String a = registrar("cruce-a", "cruce-a@elcorte.co");
        String b = registrar("cruce-b", "cruce-b@elcorte.co");

        tokenA = JsonPath.read(a, "$.accessToken");
        tokenB = JsonPath.read(b, "$.accessToken");

        String sedesDeB = mockMvc.perform(get(RUTA)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        sedeDeB = JsonPath.read(sedesDeB, "$[0].id");
        nombreOriginalDeB = JsonPath.read(sedesDeB, "$[0].name");
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

    // --- Sin credencial valida: 401 en el endpoint real ---

    @Test
    @DisplayName("sin token la API responde 401")
    void sinTokenResponde401() throws Exception {
        mockMvc.perform(get(RUTA))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("con un token que no es un JWT la API responde 401")
    void tokenInvalidoResponde401() throws Exception {
        mockMvc.perform(get(RUTA)
                .header(HttpHeaders.AUTHORIZATION, "Bearer esto.no.es-un-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("con un token vencido la API responde 401")
    void tokenVencidoResponde401() throws Exception {
        mockMvc.perform(get(RUTA)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenVencido()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    // --- Fuga entre negocios: A no puede leer ni tocar lo de B ---

    @Test
    @DisplayName("A no puede leer la sede de B aunque conozca su id")
    void aNoPuedeLeerLaSedeDeB() throws Exception {
        // Control: con su propio token, A si opera sobre lo suyo
        mockMvc.perform(get(RUTA)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isOk());

        // La sede de B no existe para A: 404, ni siquiera 403, para no delatar
        // que ese id existe en otra barberia
        mockMvc.perform(get(RUTA + "/" + sedeDeB)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("A no puede editar, cerrar ni reabrir la sede de B, y el dato de B queda intacto")
    void aNoPuedeModificarLaSedeDeB() throws Exception {
        // A intenta apoderarse de la sede de B por cada verbo de escritura
        mockMvc.perform(put(RUTA + "/" + sedeDeB)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "Secuestrada por A", "address": "otra", "city": "otra"}
                        """))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete(RUTA + "/" + sedeDeB)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isNotFound());

        mockMvc.perform(post(RUTA + "/" + sedeDeB + "/reactivate")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isNotFound());

        // La prueba de fondo: desde el lado de B, su sede no se movio. Sigue
        // con el nombre original y activa. Si el aislamiento se rompiera, aqui
        // apareceria el nombre de A o la sede cerrada.
        mockMvc.perform(get(RUTA + "/" + sedeDeB)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(nombreOriginalDeB))
                .andExpect(jsonPath("$.active").value(true));
    }

    /**
     * Emite un token de acceso bien firmado con la llave real pero ya vencido.
     * Prueba que la validez temporal se revisa de verdad, sin depender de
     * esperar a que caduque uno emitido por el login.
     */
    private String tokenVencido() {
        Instant ahora = Instant.now();

        JwtClaimsSet vencido = JwtClaimsSet.builder()
                .issuer("guardao")
                .issuedAt(ahora.minusSeconds(3600))
                .expiresAt(ahora.minusSeconds(60))
                .subject(UUID.randomUUID().toString())
                .claim(TokenService.CLAIM_TOKEN_TYPE, TokenService.TYPE_ACCESS)
                .claim(TokenService.CLAIM_ROLE, "OWNER")
                .claim(TokenService.CLAIM_BUSINESS_ID, UUID.randomUUID().toString())
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, vencido)).getTokenValue();
    }
}
