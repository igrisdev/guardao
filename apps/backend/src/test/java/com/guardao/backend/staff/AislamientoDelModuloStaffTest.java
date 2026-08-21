package com.guardao.backend.staff;

import static org.assertj.core.api.Assertions.assertThat;

import com.guardao.backend.business.BusinessRegistrationService;
import com.guardao.backend.business.LocationRepository;
import com.guardao.backend.business.RegistrationCommand;
import com.guardao.backend.business.RegistrationResult;
import com.guardao.backend.shared.tenant.TenantContext;
import com.guardao.backend.support.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * GUA-31 — Aislamiento por negocio de barberos, servicios y habilidades.
 *
 * Existe por una razon concreta. Location se filtra por su propia columna
 * business_id, que es directo; estas tres tablas no la tienen y su filtro
 * tiene que llegar al negocio por subconsulta: el barbero y el servicio
 * saltando a la sede, y la habilidad saltando al barbero y de ahi a la sede.
 * Una subconsulta mal escrita no da error visible — devuelve de mas.
 *
 * Los tests de los controladores no cubren esto: ahi la sede se verifica
 * antes y la peticion ya sale con 404 sin llegar a consultar. Este entra por
 * el repositorio, que es por donde va a entrar el codigo de las tareas
 * siguientes (disponibilidad, citas), y ahi el filtro es la unica defensa
 * (ADR-004).
 *
 * Sin @Transactional en la clase y por la misma razon que en
 * TenantIsolationTest: el filtro se enciende al abrir la transaccion, asi que
 * el negocio tiene que estar resuelto antes.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AislamientoDelModuloStaffTest extends IntegrationTest {

    @Autowired
    private BusinessRegistrationService registro;

    @Autowired
    private LocationRepository sedes;

    @Autowired
    private StaffRepository barberos;

    @Autowired
    private ServiceRepository servicios;

    @Autowired
    private SkillRepository habilidades;

    private UUID negocioPropio;
    private UUID sedePropia;
    private UUID sedeAjena;
    private UUID barberoPropio;
    private UUID barberoAjeno;
    private UUID servicioPropio;
    private UUID servicioAjeno;
    private UUID habilidadPropia;
    private UUID habilidadAjena;

    @BeforeAll
    void prepararDosNegociosConEquipo() {
        // El alta ocurre sin negocio resuelto, como en el registro real
        TenantContext.clear();

        RegistrationResult propio = registro.register(new RegistrationCommand(
                "Barberia Propia", "staff-aislamiento-propia", "Sede Propia",
                null, "Cali", "staff-aislamiento-propia@elcorte.co", "clave-segura-123"));

        RegistrationResult ajeno = registro.register(new RegistrationCommand(
                "Barberia Ajena", "staff-aislamiento-ajena", "Sede Ajena",
                null, "Cali", "staff-aislamiento-ajena@elcorte.co", "clave-segura-123"));

        negocioPropio = propio.businessId();
        sedePropia = sedes.findByBusinessId(propio.businessId()).getFirst().getId();
        sedeAjena = sedes.findByBusinessId(ajeno.businessId()).getFirst().getId();

        barberoPropio = barberos.save(new Staff(sedePropia, "Barbero Propio")).getId();
        barberoAjeno = barberos.save(new Staff(sedeAjena, "Barbero Ajeno")).getId();

        servicioPropio = servicios.save(new Service(sedePropia, "Corte Propio", 25000, 30)).getId();
        servicioAjeno = servicios.save(new Service(sedeAjena, "Corte Ajeno", 25000, 30)).getId();

        habilidadPropia = habilidades.save(new Skill(barberoPropio, servicioPropio)).getId();
        habilidadAjena = habilidades.save(new Skill(barberoAjeno, servicioAjeno)).getId();
    }

    @AfterEach
    void soltarElNegocio() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("no carga el barbero de otra barberia ni con su identificador exacto")
    void noCargaElBarberoAjeno() {
        TenantContext.set(negocioPropio);

        assertThat(barberos.findById(barberoAjeno))
                .as("el barbero existe, pero su sede es de otro negocio")
                .isEmpty();

        assertThat(barberos.findById(barberoPropio))
                .as("el suyo si lo carga, para descartar que el filtro este tapando todo")
                .isPresent();
    }

    @Test
    @DisplayName("no carga el servicio de otra barberia ni con su identificador exacto")
    void noCargaElServicioAjeno() {
        TenantContext.set(negocioPropio);

        assertThat(servicios.findById(servicioAjeno)).isEmpty();
        assertThat(servicios.findById(servicioPropio)).isPresent();
    }

    @Test
    @DisplayName("no carga la habilidad de otra barberia, que esta dos saltos mas lejos")
    void noCargaLaHabilidadAjena() {
        TenantContext.set(negocioPropio);

        assertThat(habilidades.findById(habilidadAjena)).isEmpty();
        assertThat(habilidades.findById(habilidadPropia)).isPresent();
    }

    @Test
    @DisplayName("tampoco pidiendo explicitamente la sede ajena")
    void tampocoPidiendoExplicitamenteLaSedeAjena() {
        TenantContext.set(negocioPropio);

        assertThat(barberos.findByLocationIdOrderByNameAsc(sedeAjena))
                .as("la consulta pide otra sede, y aun asi no devuelve nada")
                .isEmpty();

        assertThat(servicios.findByLocationIdOrderByNameAsc(sedeAjena)).isEmpty();

        assertThat(barberos.findByIdAndLocationId(barberoAjeno, sedeAjena)).isEmpty();
        assertThat(servicios.findByIdAndLocationId(servicioAjeno, sedeAjena)).isEmpty();
    }

    @Test
    @DisplayName("al listar sin filtro de sede solo aparece lo propio")
    void alListarSoloApareceLoPropio() {
        TenantContext.set(negocioPropio);

        assertThat(barberos.findAll())
                .extracting(Staff::getId)
                .containsExactly(barberoPropio);

        assertThat(servicios.findAll())
                .extracting(Service::getId)
                .containsExactly(servicioPropio);

        assertThat(habilidades.findAll())
                .extracting(Skill::getId)
                .containsExactly(habilidadPropia);
    }
}
