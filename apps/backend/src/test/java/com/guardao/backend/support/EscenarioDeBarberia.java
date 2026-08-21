package com.guardao.backend.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * GUA-40 — Una barberia lista para probar sobre ella.
 *
 * Los tests de esta etapa necesitan casi siempre el mismo montaje: registrar
 * un negocio, sacar su sede, y crear dentro un barbero, un servicio, una
 * habilidad y un horario. Repetirlo en cada clase son cuarenta lineas de ruido
 * antes de llegar a lo que el test viene a comprobar, y ademas invita a que
 * cada una lo monte un poco distinto — que es como terminan dos tests
 * pareciendo equivalentes y no siendolo.
 *
 * Devuelve identificadores en crudo y no objetos de dominio a proposito: estos
 * tests hablan por HTTP, igual que el frontend, y lo unico que necesitan de la
 * respuesta es el id para armar la siguiente URL.
 */
public class EscenarioDeBarberia {

    private final MockMvc mockMvc;
    private final String token;
    private final UUID businessId;
    private final UUID locationId;

    public EscenarioDeBarberia(MockMvc mockMvc, String slug, String correo) throws Exception {
        this.mockMvc = mockMvc;

        String sesion = mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "businessName": "Barberia %s",
                          "slug": "%s",
                          "locationName": "Sede Original",
                          "email": "%s",
                          "password": "clave-segura-123"
                        }
                        """.formatted(slug, slug, correo)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        this.token = JsonPath.read(sesion, "$.accessToken");
        this.businessId = UUID.fromString(JsonPath.read(sesion, "$.businessId"));

        String sedes = mockMvc.perform(get("/api/v1/locations")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        this.locationId = UUID.fromString(JsonPath.read(sedes, "$[0].id"));
    }

    public String token() {
        return token;
    }

    public UUID businessId() {
        return businessId;
    }

    /** La sede que creo el registro. */
    public UUID locationId() {
        return locationId;
    }

    public String ruta(String sufijo) {
        return "/api/v1/locations/" + locationId + sufijo;
    }

    public UUID crearBarbero(String nombre) throws Exception {
        String json = mockMvc.perform(post(ruta("/staff"))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "%s"}
                        """.formatted(nombre)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return UUID.fromString(JsonPath.read(json, "$.id"));
    }

    public UUID crearServicio(String nombre, int precio, int duracionMin) throws Exception {
        String json = mockMvc.perform(post(ruta("/services"))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "%s", "price": %d, "durationMin": %d}
                        """.formatted(nombre, precio, duracionMin)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return UUID.fromString(JsonPath.read(json, "$.id"));
    }

    public void asignarHabilidad(UUID staffId, UUID serviceId) throws Exception {
        mockMvc.perform(put(ruta("/staff/" + staffId + "/skills/" + serviceId))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    /** Horario de la sede. Se manda la semana entera, como pide el endpoint. */
    public void horarioDeLaSede(String franjasJson) throws Exception {
        mockMvc.perform(put(ruta("/schedule"))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"slots\": [%s]}".formatted(franjasJson)))
                .andExpect(status().isOk());
    }

    public void horarioDelBarbero(UUID staffId, String franjasJson) throws Exception {
        mockMvc.perform(put(ruta("/staff/" + staffId + "/schedule"))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"slots\": [%s]}".formatted(franjasJson)))
                .andExpect(status().isOk());
    }

    public void bloquear(UUID staffId, String desdeIso, String hastaIso, String motivo)
            throws Exception {
        mockMvc.perform(post(ruta("/staff/" + staffId + "/blocks"))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"startAt": "%s", "endAt": "%s", "reason": "%s"}
                        """.formatted(desdeIso, hastaIso, motivo)))
                .andExpect(status().isCreated());
    }

    /** Una franja para el cuerpo de un horario, ya en JSON. */
    public static String franja(int dayOfWeek, String abre, String cierra) {
        return """
                {"dayOfWeek": %d, "openTime": "%s", "closeTime": "%s"}
                """.formatted(dayOfWeek, abre, cierra);
    }
}
