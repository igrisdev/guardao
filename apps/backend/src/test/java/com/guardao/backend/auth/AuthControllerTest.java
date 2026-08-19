package com.guardao.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.guardao.backend.business.BusinessRepository;
import com.guardao.backend.business.LocationRepository;
import com.guardao.backend.business.UserRepository;
import com.guardao.backend.business.UserRole;
import com.guardao.backend.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * GUA-20 — Contrato HTTP del registro.
 *
 * No lleva @Transactional a proposito: se quiere ver lo que queda realmente
 * guardado despues de que el endpoint responde, no lo que se ve dentro de
 * una transaccion que luego se deshace. Por eso cada prueba usa su propio
 * slug y correo.
 */
@AutoConfigureMockMvc
class AuthControllerTest extends IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BusinessRepository businesses;

    @Autowired
    private LocationRepository locations;

    @Autowired
    private UserRepository users;

    @Autowired
    private JwtDecoder jwtDecoder;

    private static String cuerpo(String slug, String email) {
        return """
                {
                  "businessName": "Barberia El Corte",
                  "slug": "%s",
                  "locationName": "Sede Centro",
                  "address": "Calle 10 # 5-20",
                  "city": "Cali",
                  "email": "%s",
                  "password": "clave-segura-123"
                }
                """.formatted(slug, email);
    }

    @Test
    @DisplayName("un registro valido crea todo y devuelve la sesion iniciada")
    void registroValidoCreaTodoYDevuelveSesion() throws Exception {
        MvcResult resultado = mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo("http-el-corte", "http-dueno@elcorte.co")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.role").value("OWNER"))
                .andExpect(jsonPath("$.businessSlug").value("http-el-corte"))
                .andExpect(jsonPath("$.expiresInSeconds").isNumber())
                // La respuesta no debe filtrar la contraseña de vuelta
                .andExpect(jsonPath("$.password").doesNotExist())
                .andReturn();

        String json = resultado.getResponse().getContentAsString();
        UUID businessId = UUID.fromString(JsonPath.read(json, "$.businessId"));
        UUID userId = UUID.fromString(JsonPath.read(json, "$.userId"));

        // Las tres filas quedaron realmente guardadas
        assertThat(businesses.findById(businessId)).isPresent();
        assertThat(locations.findByBusinessId(businessId)).hasSize(1);
        assertThat(users.findById(userId))
                .get()
                .satisfies(dueno -> assertThat(dueno.getRole()).isEqualTo(UserRole.OWNER));

        // La sesion sirve de verdad: el token es valido y lleva el contexto
        Jwt token = jwtDecoder.decode(JsonPath.read(json, "$.accessToken"));
        assertThat(token.getSubject()).isEqualTo(userId.toString());
        assertThat(token.getClaimAsString(TokenService.CLAIM_BUSINESS_ID)).isEqualTo(businessId.toString());
        assertThat(token.getClaimAsString(TokenService.CLAIM_ROLE)).isEqualTo("OWNER");
        assertThat(TokenService.isAccessToken(token)).isTrue();
    }

    @Test
    @DisplayName("un slug repetido devuelve 409 diciendo cual campo fallo")
    void slugRepetidoDevuelveErrorClaro() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo("http-repetido", "primero@elcorte.co")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo("http-repetido", "segundo@elcorte.co")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SLUG_TAKEN"))
                .andExpect(jsonPath("$.details.field").value("slug"));
    }

    @Test
    @DisplayName("un correo repetido se distingue del slug repetido")
    void correoRepetidoDevuelveSuPropioCodigo() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo("http-correo-uno", "http-mismo@elcorte.co")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo("http-correo-dos", "http-mismo@elcorte.co")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
    }

    @Test
    @DisplayName("un slug con mayusculas o espacios no pasa la validacion")
    void slugConFormatoInvalidoDevuelve400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo("Barberia El Corte", "formato@elcorte.co")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("una contraseña corta no pasa la validacion")
    void contrasenaCortaDevuelve400() throws Exception {
        String cuerpo = """
                {
                  "businessName": "Barberia El Corte",
                  "slug": "http-clave-corta",
                  "locationName": "Sede Centro",
                  "email": "corta@elcorte.co",
                  "password": "1234"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        assertThat(businesses.existsBySlug("http-clave-corta"))
                .as("una peticion invalida no debe dejar nada creado")
                .isFalse();
    }
}
