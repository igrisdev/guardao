package com.guardao.backend.business;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.guardao.backend.support.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * GUA-24 — Creacion del personal interno de Guardao.
 *
 * El ticket pide dos cosas: que exista una forma reproducible de crear un
 * SUPER_ADMIN, y que ninguna ruta HTTP permita hacerlo. Aqui se comprueban
 * ambas, y de paso que la base sostenga la regla por su cuenta.
 *
 * El seeder se instancia a mano en vez de levantar otro contexto de Spring
 * con las propiedades puestas: probar la logica no justifica arrancar un
 * segundo contenedor de Postgres. Que Spring lo ejecute al arrancar lo
 * garantiza el ApplicationRunner, no un test.
 */
@AutoConfigureMockMvc
class SuperAdminSeederTest extends IntegrationTest {

    private static final String CLAVE = "clave-interna-muy-larga";

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mockMvc;

    private SuperAdminSeeder seeder(String email, String clave) {
        return new SuperAdminSeeder(
                new SuperAdminProperties(email, clave), users, passwordEncoder);
    }

    @Test
    @DisplayName("crea el super-admin sin negocio y con la clave hasheada")
    void creaElSuperAdminSinNegocio() {
        String correo = "admin-crear@guardao.co";
        seeder(correo, CLAVE).run(null);

        User creado = users.findByEmail(correo).orElseThrow();

        assertThat(creado.getRole()).isEqualTo(UserRole.SUPER_ADMIN);
        assertThat(creado.getBusinessId())
                .as("Guardao es la plataforma, no una barberia: no cuelga de ningun negocio")
                .isNull();
        assertThat(creado.getStaffId()).isNull();
        assertThat(creado.getPasswordHash()).isNotEqualTo(CLAVE);
        assertThat(passwordEncoder.matches(CLAVE, creado.getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("si ya existe no lo toca, para poder dejar la variable puesta")
    void siYaExisteNoLoToca() {
        String correo = "admin-idempotente@guardao.co";
        seeder(correo, CLAVE).run(null);

        String hashOriginal = users.findByEmail(correo).orElseThrow().getPasswordHash();

        // Un reinicio no debe devolverle la clave original a alguien que ya la cambio
        seeder(correo, "otra-clave-distinta-larga").run(null);

        assertThat(users.findByEmail(correo).orElseThrow().getPasswordHash())
                .isEqualTo(hashOriginal);
    }

    @Test
    @DisplayName("sin variables configuradas no hace nada")
    void sinVariablesConfiguradasNoHaceNada() {
        long antes = users.count();

        seeder(null, null).run(null);
        seeder("", "").run(null);

        assertThat(users.count()).isEqualTo(antes);
    }

    @Test
    @DisplayName("rechaza una clave corta al arrancar, no despues")
    void rechazaUnaClaveCorta() {
        assertThatThrownBy(() -> seeder("admin-clave-corta@guardao.co", "corta").run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("guardao.super-admin.password");

        assertThat(users.findByEmail("admin-clave-corta@guardao.co")).isEmpty();
    }

    @Test
    @DisplayName("el super-admin creado puede iniciar sesion")
    void elSuperAdminPuedeIniciarSesion() throws Exception {
        String correo = "admin-login@guardao.co";
        seeder(correo, CLAVE).run(null);

        String cuerpo = """
                {"email": "%s", "password": "%s"}
                """.formatted(correo, CLAVE);

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("SUPER_ADMIN"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                // Sin negocio: los campos del tenant vienen vacios
                .andExpect(jsonPath("$.businessId").doesNotExist())
                .andExpect(jsonPath("$.businessSlug").doesNotExist());
    }

    @Test
    @DisplayName("ninguna ruta publica crea un super-admin, ni mandando el rol")
    void ningunaRutaPublicaCreaUnSuperAdmin() throws Exception {
        String cuerpo = """
                {
                  "businessName": "Barberia Colada",
                  "slug": "colada",
                  "locationName": "Sede Colada",
                  "email": "colada@elcorte.co",
                  "password": "clave-segura-123",
                  "role": "SUPER_ADMIN"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("OWNER"));

        assertThat(users.findByEmail("colada@elcorte.co").orElseThrow().getRole())
                .as("el rol del registro esta fijo en el codigo; lo que venga en el cuerpo se ignora")
                .isEqualTo(UserRole.OWNER);
    }

    @Test
    @DisplayName("la base rechaza un super-admin atado a una barberia")
    void laBaseRechazaUnSuperAdminConNegocio() {
        UUID negocio = insertarNegocio();

        assertThatThrownBy(() -> insertarUsuario(negocio, "SUPER_ADMIN", "pegado@guardao.co"))
                .as("lo impide el CHECK app_user_business_only_for_tenant_roles")
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("y tambien rechaza un dueño sin barberia")
    void laBaseRechazaUnDuenoSinNegocio() {
        assertThatThrownBy(() -> insertarUsuario(null, "OWNER", "suelto@elcorte.co"))
                .as("la restriccion vale en los dos sentidos, no solo para SUPER_ADMIN")
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /** Una barberia cualquiera a la que intentar atar el super-admin. */
    private UUID insertarNegocio() {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO business (id, name, slug, referral_code)
                VALUES (?, 'Barberia Cualquiera', ?, ?)
                """, id, "cualquiera-" + id, id.toString().substring(0, 8).toUpperCase());
        return id;
    }

    /** Inserta saltandose la entidad, para probar la regla de la base y no la del codigo. */
    private void insertarUsuario(UUID businessId, String rol, String correo) {
        jdbc.update("""
                INSERT INTO app_user (id, business_id, email, password_hash, role)
                VALUES (?, ?, ?, 'hash-de-mentira', ?)
                """, UUID.randomUUID(), businessId, correo, rol);
    }
}
