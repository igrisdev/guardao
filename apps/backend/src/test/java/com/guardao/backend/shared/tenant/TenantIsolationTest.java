package com.guardao.backend.shared.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import com.guardao.backend.business.BusinessRegistrationService;
import com.guardao.backend.business.BusinessRepository;
import com.guardao.backend.business.Location;
import com.guardao.backend.business.LocationRepository;
import com.guardao.backend.business.RegistrationCommand;
import com.guardao.backend.business.RegistrationResult;
import com.guardao.backend.business.UserRepository;
import com.guardao.backend.support.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * GUA-22 — Aislamiento entre negocios a nivel de consulta.
 *
 * Es la prueba de la pieza mas critica del sistema: si esto falla, una
 * barberia ve los datos de otra (ADR-004). Por eso cada caso ataca una via
 * distinta de llegar a la fila ajena, no solo la mas obvia.
 *
 * La clase no lleva @Transactional, y no es un descuido. El filtro se
 * enciende al abrir la transaccion, asi que el negocio tiene que estar
 * resuelto antes; con una transaccion abierta para todo el test, se fijaria
 * el negocio demasiado tarde y el filtro nunca se activaria. Sin ella, cada
 * llamada al repositorio abre su propia transaccion, que es justo lo que
 * ocurre en una peticion real.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TenantIsolationTest extends IntegrationTest {

    @Autowired
    private BusinessRegistrationService registro;

    @Autowired
    private BusinessRepository businesses;

    @Autowired
    private LocationRepository locations;

    @Autowired
    private UserRepository users;

    private RegistrationResult negocioPropio;
    private RegistrationResult negocioAjeno;
    private UUID sedePropia;
    private UUID sedeAjena;

    @BeforeAll
    void prepararDosNegocios() {
        // El alta ocurre sin negocio resuelto, como en el registro real
        TenantContext.clear();

        negocioPropio = registro.register(new RegistrationCommand(
                "Barberia Propia", "aislamiento-propia", "Sede Propia",
                null, "Cali", "propia@elcorte.co", "clave-segura-123"));

        negocioAjeno = registro.register(new RegistrationCommand(
                "Barberia Ajena", "aislamiento-ajena", "Sede Ajena",
                null, "Cali", "ajena@elcorte.co", "clave-segura-123"));

        sedePropia = locations.findByBusinessId(negocioPropio.businessId()).getFirst().getId();
        sedeAjena = locations.findByBusinessId(negocioAjeno.businessId()).getFirst().getId();
    }

    @AfterEach
    void soltarElNegocio() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("al listar sedes solo aparecen las propias")
    void alListarSedesSoloAparecenLasPropias() {
        TenantContext.set(negocioPropio.businessId());

        assertThat(locations.findAll())
                .extracting(Location::getId)
                .as("en la base hay sedes de varias barberias y solo debe ver la suya")
                .containsExactly(sedePropia);
    }

    @Test
    @DisplayName("no puede cargar la sede ajena aunque tenga su identificador exacto")
    void noPuedeCargarLaSedeAjenaPorSuIdentificador() {
        TenantContext.set(negocioPropio.businessId());

        // Este es el caso que se escapa con la configuracion por omision de
        // Hibernate: los filtros no se aplican al cargar por clave primaria.
        // Si applyToLoadByKey dejara de estar, este test se pone rojo.
        assertThat(locations.findById(sedeAjena))
                .as("con el id exacto de la sede de otro negocio, la respuesta debe ser vacia")
                .isEmpty();

        assertThat(locations.findById(sedePropia))
                .as("la suya si la carga, para descartar que este fallando la consulta")
                .isPresent();
    }

    @Test
    @DisplayName("tampoco pidiendo explicitamente el negocio ajeno")
    void tampocoPidiendoExplicitamenteElNegocioAjeno() {
        TenantContext.set(negocioPropio.businessId());

        assertThat(locations.findByBusinessId(negocioAjeno.businessId()))
                .as("la consulta pide otro negocio, y aun asi no devuelve nada")
                .isEmpty();
    }

    @Test
    @DisplayName("no puede cargar el negocio ajeno, pero si el suyo")
    void noPuedeCargarElNegocioAjenoPeroSiElSuyo() {
        TenantContext.set(negocioPropio.businessId());

        assertThat(businesses.findById(negocioAjeno.businessId())).isEmpty();
        assertThat(businesses.findById(negocioPropio.businessId())).isPresent();
    }

    @Test
    @DisplayName("no puede ver los usuarios de otro negocio ni buscando por correo")
    void noPuedeVerLosUsuariosDeOtroNegocio() {
        TenantContext.set(negocioPropio.businessId());

        assertThat(users.findByEmail("ajena@elcorte.co"))
                .as("el correo existe, pero es de otro negocio")
                .isEmpty();
        assertThat(users.findByEmail("propia@elcorte.co")).isPresent();
    }

    @Test
    @DisplayName("sin negocio resuelto no se filtra, que es lo que necesita el login")
    void sinNegocioResueltoNoSeFiltra() {
        // El login busca por correo en toda la plataforma: todavia no sabe de
        // que negocio es quien escribe
        assertThat(TenantContext.hasTenant()).isFalse();

        assertThat(users.findByEmail("ajena@elcorte.co")).isPresent();
        assertThat(businesses.findById(negocioAjeno.businessId())).isPresent();
        assertThat(locations.findById(sedeAjena)).isPresent();
    }
}
