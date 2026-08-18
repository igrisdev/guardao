package com.guardao.backend.business;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.guardao.backend.support.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * GUA-19 — Las tres entidades base responden contra el esquema real.
 *
 * Que el contexto arranque ya prueba media tarea: el perfil test corre con
 * ddl-auto validate, asi que Hibernate compara cada mapeo contra las tablas
 * que creo Flyway y falla al iniciar si alguno no cuadra.
 *
 * Cada test corre en su propia transaccion y se deshace al terminar, para
 * que el orden entre ellos no cambie el resultado.
 */
@Transactional
class BusinessRepositoriesTest extends IntegrationTest {

    @Autowired
    private BusinessRepository businesses;

    @Autowired
    private LocationRepository locations;

    @Autowired
    private UserRepository users;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("guarda un negocio y lo recupera por su slug")
    void guardaNegocioYLoEncuentraPorSlug() {
        // saveAndFlush y no save: las marcas de tiempo las pone Hibernate al
        // mandar el INSERT, y save() por si solo lo deja pendiente
        Business guardado = businesses.saveAndFlush(new Business("Barberia El Corte", "el-corte", "CORTE10"));

        assertThat(guardado.getId()).as("el identificador lo asigna la aplicacion al guardar").isNotNull();
        assertThat(guardado.getCreatedAt()).isNotNull();
        assertThat(guardado.getUpdatedAt()).isNotNull();
        assertThat(guardado.getType()).as("por defecto es una barberia").isEqualTo("BARBERSHOP");

        assertThat(businesses.findBySlug("el-corte")).contains(guardado);
        assertThat(businesses.existsBySlug("el-corte")).isTrue();
        assertThat(businesses.existsBySlug("no-existe")).isFalse();
    }

    @Test
    @DisplayName("una sede no es visible desde otro negocio")
    void sedeNoEsVisibleDesdeOtroNegocio() {
        Business propio = businesses.save(new Business("Propio", "propio", "PROPIO1"));
        Business ajeno = businesses.save(new Business("Ajeno", "ajeno", "AJENO1"));

        Location sede = locations.save(new Location(propio.getId(), "Sede Centro"));

        assertThat(locations.findByIdAndBusinessId(sede.getId(), propio.getId()))
                .as("su propio negocio si la ve")
                .contains(sede);

        assertThat(locations.findByIdAndBusinessId(sede.getId(), ajeno.getId()))
                .as("con el identificador correcto pero otro negocio, no devuelve nada (ADR-004)")
                .isEmpty();

        assertThat(locations.findByBusinessId(ajeno.getId())).isEmpty();
    }

    @Test
    @DisplayName("lista solo las sedes activas cuando se piden asi")
    void listaSolamenteSedesActivas() {
        Business negocio = businesses.save(new Business("Dos Sedes", "dos-sedes", "DOS1"));
        locations.save(new Location(negocio.getId(), "Abierta"));

        Location cerrada = new Location(negocio.getId(), "Cerrada");
        cerrada.setActive(false);
        locations.save(cerrada);

        assertThat(locations.findByBusinessId(negocio.getId())).hasSize(2);
        assertThat(locations.findByBusinessIdAndActiveTrue(negocio.getId()))
                .extracting(Location::getName)
                .containsExactly("Abierta");
    }

    @Test
    @DisplayName("guarda un dueño y lo encuentra por correo, con el rol intacto")
    void guardaDuenoYLoEncuentraPorCorreo() {
        Business negocio = businesses.save(new Business("Con Dueño", "con-dueno", "DUENO1"));

        User dueno = users.save(new User(
                negocio.getId(), "dueno@elcorte.co", "hash-no-es-la-clave", UserRole.OWNER));

        assertThat(users.findByEmail("dueno@elcorte.co")).contains(dueno);
        assertThat(users.existsByEmail("dueno@elcorte.co")).isTrue();

        User recuperado = users.findByEmail("dueno@elcorte.co").orElseThrow();
        assertThat(recuperado.getRole()).isEqualTo(UserRole.OWNER);
        assertThat(recuperado.getStaffId()).as("un dueño no es un barbero").isNull();

        // El rol viaja como texto, no como posicion del enum
        String rolEnLaTabla = jdbc.queryForObject(
                "SELECT role FROM app_user WHERE id = ?", String.class, recuperado.getId());
        assertThat(rolEnLaTabla).isEqualTo("OWNER");
    }

    @Test
    @DisplayName("el login de un barbero queda atado a su registro de staff")
    void loginDeBarberoQuedaAtadoASuStaff() {
        Business negocio = businesses.save(new Business("Con Staff", "con-staff", "STAFF1"));
        // El INSERT de staff va por SQL directo, asi que la sede tiene que
        // estar en la base antes: sin el flush, su llave foranea no encuentra nada
        Location sede = locations.saveAndFlush(new Location(negocio.getId(), "Sede Unica"));
        UUID staffId = insertarStaff(sede.getId(), "Pedro");

        User barbero = users.save(User.forStaff(
                negocio.getId(), "pedro@elcorte.co", "hash-no-es-la-clave", staffId));

        assertThat(barbero.getRole()).isEqualTo(UserRole.STAFF);
        assertThat(users.findByStaffId(staffId)).contains(barbero);
    }

    @Test
    @DisplayName("la base rechaza un STAFF que no apunta a ningun barbero")
    void baseRechazaStaffSinBarbero() {
        Business negocio = businesses.save(new Business("Sin Staff", "sin-staff", "SINST1"));

        // Se salta forStaff a proposito, que es justo lo que la restriccion cuida
        User invalido = new User(
                negocio.getId(), "suelto@elcorte.co", "hash-no-es-la-clave", UserRole.STAFF);

        assertThatThrownBy(() -> users.saveAndFlush(invalido))
                .as("lo impide el CHECK app_user_staff_only_for_staff_role")
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /**
     * Inserta un barbero a mano: la entidad Staff es de la Etapa 2, pero la
     * llave foranea de app_user.staff_id ya exige que la fila exista.
     */
    private UUID insertarStaff(UUID locationId, String nombre) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO staff (id, location_id, name) VALUES (?, ?, ?)", id, locationId, nombre);
        return id;
    }
}
