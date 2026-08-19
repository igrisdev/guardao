package com.guardao.backend.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;

import com.guardao.backend.support.IntegrationTest;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * GUA-29 — Validacion del token en la puerta de la API.
 *
 * TokenServiceTest cubre la emision y la lectura de un token bien formado.
 * Aqui se cubre el otro lado: que la cadena de seguridad rechace los tokens
 * que no debe aceptar. Todos los caminos de rechazo terminan igual (401 con
 * codigo UNAUTHENTICATED y sin decir por que), que es justo lo que el
 * SecurityErrorResponder promete: no darle pistas a quien prueba tokens.
 *
 * Se ejerce sobre una ruta protegida sin controlador: sin credencial valida
 * responde 401, y con una valida llega hasta el enrutador y responde 404. El
 * 404 es entonces la prueba de que la autenticacion paso.
 */
@AutoConfigureMockMvc
class TokenValidationTest extends IntegrationTest {

    private static final String RUTA_PROTEGIDA = "/api/v1/una-ruta-protegida";

    @Autowired
    private MockMvc mockMvc;

    /** Firmador real de la aplicacion: emite tokens con la firma que el decoder espera. */
    @Autowired
    private JwtEncoder firmadorReal;

    // firmadorReal lo inyecta Spring; firmadorAjeno se construye a mano abajo.

    /**
     * Firmador con un secreto distinto pero igual de valido en longitud. Sirve
     * para fabricar un token con firma correcta en si misma, pero ajena: es el
     * caso del token falsificado por quien no conoce el secreto de Guardao.
     */
    private final JwtEncoder firmadorAjeno = new NimbusJwtEncoder(new ImmutableSecret<>(
            new SecretKeySpec("otro-secreto-de-32-caracteres-min".getBytes(), "HmacSHA256")));

    /** Claims de un token de acceso valido; cada prueba ajusta lo que quiere romper. */
    private static JwtClaimsSet.Builder accesoValido() {
        Instant ahora = Instant.now();
        return JwtClaimsSet.builder()
                .issuer("guardao")
                .issuedAt(ahora)
                .expiresAt(ahora.plusSeconds(300))
                .subject(UUID.randomUUID().toString())
                .claim(TokenService.CLAIM_TOKEN_TYPE, TokenService.TYPE_ACCESS)
                .claim(TokenService.CLAIM_ROLE, "OWNER")
                .claim(TokenService.CLAIM_BUSINESS_ID, UUID.randomUUID().toString());
    }

    private static String firmar(JwtEncoder firmador, JwtClaimsSet claims) {
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return firmador.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private void esperarRechazo(String token) throws Exception {
        mockMvc.perform(get(RUTA_PROTEGIDA)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized())
                // El motivo real no se filtra: firma mala, vencido o incompleto
                // responden con el mismo codigo generico
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("un token de acceso bien firmado llega hasta el enrutador (control)")
    void tokenValidoLlegaAlEnrutador() throws Exception {
        String valido = firmar(firmadorReal, accesoValido().build());

        // 404 y no 401: la autenticacion paso, solo que la ruta no existe. Este
        // es el control que le da sentido a los rechazos de las demas pruebas.
        mockMvc.perform(get(RUTA_PROTEGIDA)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + valido))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("un token firmado con otro secreto es rechazado")
    void tokenFirmadoConOtroSecretoEsRechazado() throws Exception {
        esperarRechazo(firmar(firmadorAjeno, accesoValido().build()));
    }

    @Test
    @DisplayName("un token de acceso vencido es rechazado")
    void tokenVencidoEsRechazado() throws Exception {
        Instant ahora = Instant.now();
        JwtClaimsSet vencido = accesoValido()
                .issuedAt(ahora.minusSeconds(3600))
                .expiresAt(ahora.minusSeconds(60))
                .build();

        esperarRechazo(firmar(firmadorReal, vencido));
    }

    @Test
    @DisplayName("un token de firma valida pero manipulado en la firma es rechazado")
    void tokenConFirmaAlteradaEsRechazado() throws Exception {
        String valido = firmar(firmadorReal, accesoValido().build());

        // Se altera el primer caracter del segmento de firma (el tercero). El
        // payload queda intacto y legible, pero la firma deja de cuadrar con el
        // contenido. Se toca el primer caracter y no el ultimo porque el ultimo
        // arrastra bits de relleno de base64 que podrian no cambiar la firma.
        String[] partes = valido.split("\\.");
        char primero = partes[2].charAt(0);
        partes[2] = (primero == 'A' ? 'B' : 'A') + partes[2].substring(1);
        String alterado = partes[0] + "." + partes[1] + "." + partes[2];

        esperarRechazo(alterado);
    }

    @Test
    @DisplayName("un token sin el claim de tipo no sirve como credencial")
    void tokenSinTipoEsRechazado() throws Exception {
        Instant ahora = Instant.now();
        JwtClaimsSet sinTipo = JwtClaimsSet.builder()
                .issuer("guardao")
                .issuedAt(ahora)
                .expiresAt(ahora.plusSeconds(300))
                .subject(UUID.randomUUID().toString())
                .claim(TokenService.CLAIM_ROLE, "OWNER")
                .build();

        esperarRechazo(firmar(firmadorReal, sinTipo));
    }

    @Test
    @DisplayName("un token de acceso sin rol es rechazado")
    void tokenSinRolEsRechazado() throws Exception {
        Instant ahora = Instant.now();
        JwtClaimsSet sinRol = JwtClaimsSet.builder()
                .issuer("guardao")
                .issuedAt(ahora)
                .expiresAt(ahora.plusSeconds(300))
                .subject(UUID.randomUUID().toString())
                .claim(TokenService.CLAIM_TOKEN_TYPE, TokenService.TYPE_ACCESS)
                .build();

        esperarRechazo(firmar(firmadorReal, sinRol));
    }

    @Test
    @DisplayName("una cadena que no es un JWT es rechazada")
    void cadenaQueNoEsTokenEsRechazada() throws Exception {
        esperarRechazo("esto.no.es-un-token");
    }
}
