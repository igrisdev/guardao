package com.guardao.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * GUA-21 — Contrato HTTP del login y el refresco.
 *
 * Sin @Transactional: interesa lo que queda guardado de verdad despues de
 * cada respuesta, no lo que se ve dentro de una transaccion que se deshace.
 */
@AutoConfigureMockMvc
// Una sola instancia para toda la clase, que es lo que permite registrar la
// barberia una vez en @BeforeAll: el correo y el slug son unicos
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LoginAndRefreshTest extends IntegrationTest {

    private static final String CORREO = "login@elcorte.co";
    private static final String CLAVE = "clave-segura-123";

    /**
     * Ruta protegida cualquiera que no tiene controlador. Sirve para probar el
     * filtro de seguridad sin inventar un endpoint: sin token responde 401, y
     * con un token valido llega hasta el enrutador y responde 404.
     */
    private static final String RUTA_PROTEGIDA = "/api/v1/una-ruta-protegida";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtDecoder jwtDecoder;

    @BeforeAll
    void registrarBarberia() throws Exception {
        String cuerpo = """
                {
                  "businessName": "Barberia El Corte",
                  "slug": "login-el-corte",
                  "locationName": "Sede Centro",
                  "email": "%s",
                  "password": "%s"
                }
                """.formatted(CORREO, CLAVE);

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo))
                .andExpect(status().isCreated());
    }

    private String login(String correo, String clave) throws Exception {
        String cuerpo = """
                {"email": "%s", "password": "%s"}
                """.formatted(correo, clave);

        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    @Test
    @DisplayName("con credenciales validas devuelve una sesion con el negocio y el rol")
    void credencialesValidasDevuelvenSesion() throws Exception {
        String json = login(CORREO, CLAVE);

        Jwt token = jwtDecoder.decode(JsonPath.read(json, "$.accessToken"));

        assertThat(token.getClaimAsString(TokenService.CLAIM_ROLE)).isEqualTo("OWNER");
        assertThat(token.getClaimAsString(TokenService.CLAIM_BUSINESS_ID))
                .as("el token lleva el negocio, que es lo que aisla los datos (ADR-004)")
                .isEqualTo(JsonPath.read(json, "$.businessId"));
        assertThat(TokenService.isAccessToken(token)).isTrue();
        assertThat(JsonPath.<String>read(json, "$.businessSlug")).isEqualTo("login-el-corte");
    }

    @Test
    @DisplayName("el token que entrega el login sirve para llamar a la API")
    void elTokenDelLoginSirveParaLlamarLaApi() throws Exception {
        String accessToken = JsonPath.read(login(CORREO, CLAVE), "$.accessToken");

        // Sin token, el filtro corta la peticion
        mockMvc.perform(get(RUTA_PROTEGIDA))
                .andExpect(status().isUnauthorized());

        // Con el token, la peticion pasa el filtro y llega al enrutador, que no
        // encuentra esa ruta. El 404 es la prueba de que la autenticacion paso.
        mockMvc.perform(get(RUTA_PROTEGIDA)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("un token de refresco no sirve como credencial de la API")
    void tokenDeRefrescoNoSirveComoCredencial() throws Exception {
        String refreshToken = JsonPath.read(login(CORREO, CLAVE), "$.refreshToken");

        mockMvc.perform(get(RUTA_PROTEGIDA)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + refreshToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("la contraseña equivocada y el correo inexistente responden identico")
    void credencialesMalasNoRevelanSiElCorreoExiste() throws Exception {
        String claveMala = """
                {"email": "%s", "password": "clave-que-no-es"}
                """.formatted(CORREO);

        String correoInexistente = """
                {"email": "nadie@elcorte.co", "password": "%s"}
                """.formatted(CLAVE);

        String respuestaClaveMala = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(claveMala))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andReturn().getResponse().getContentAsString();

        String respuestaCorreoInexistente = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(correoInexistente))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<String>read(respuestaClaveMala, "$.message"))
                .as("el mensaje tampoco puede delatar cual de los dos fallo")
                .isEqualTo(JsonPath.read(respuestaCorreoInexistente, "$.message"));
    }

    @Test
    @DisplayName("el refresco entrega tokens nuevos y usables")
    void refrescoEntregaTokensNuevos() throws Exception {
        String refreshToken = JsonPath.read(login(CORREO, CLAVE), "$.refreshToken");

        String cuerpo = """
                {"refreshToken": "%s"}
                """.formatted(refreshToken);

        String json = mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("OWNER"))
                .andReturn().getResponse().getContentAsString();

        mockMvc.perform(get(RUTA_PROTEGIDA)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + JsonPath.<String>read(json, "$.accessToken")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("no se puede refrescar con un token de acceso")
    void noSePuedeRefrescarConUnTokenDeAcceso() throws Exception {
        String accessToken = JsonPath.read(login(CORREO, CLAVE), "$.accessToken");

        String cuerpo = """
                {"refreshToken": "%s"}
                """.formatted(accessToken);

        mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    @DisplayName("un token inventado no renueva nada")
    void tokenInventadoNoRenuevaNada() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\": \"esto.no.es-un-token\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }
}
