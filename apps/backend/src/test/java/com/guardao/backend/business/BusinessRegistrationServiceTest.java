package com.guardao.backend.business;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.guardao.backend.shared.error.ApiException;
import com.guardao.backend.shared.error.ErrorCode;
import com.guardao.backend.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

/**
 * GUA-20 — Alta de barberia a nivel de dominio.
 *
 * Lo que se prueba aqui es la regla de negocio; el contrato HTTP se prueba
 * en AuthControllerTest.
 */
@Transactional
class BusinessRegistrationServiceTest extends IntegrationTest {

    @Autowired
    private BusinessRegistrationService service;

    @Autowired
    private BusinessRepository businesses;

    @Autowired
    private LocationRepository locations;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private static RegistrationCommand comando(String slug, String email) {
        return new RegistrationCommand(
                "Barberia El Corte", slug, "Sede Centro",
                "Calle 10 # 5-20", "Cali", email, "clave-segura-123");
    }

    @Test
    @DisplayName("deja creadas las tres filas: negocio, sede y dueño")
    void dejaCreadasLasTresFilas() {
        RegistrationResult registro = service.register(comando("el-corte", "dueno@elcorte.co"));

        Business negocio = businesses.findById(registro.businessId()).orElseThrow();
        assertThat(negocio.getSlug()).isEqualTo("el-corte");
        assertThat(negocio.getName()).isEqualTo("Barberia El Corte");

        assertThat(locations.findByBusinessId(registro.businessId()))
                .singleElement()
                .satisfies(sede -> {
                    assertThat(sede.getName()).isEqualTo("Sede Centro");
                    assertThat(sede.getCity()).isEqualTo("Cali");
                    assertThat(sede.isActive()).isTrue();
                });

        User dueno = users.findById(registro.ownerId()).orElseThrow();
        assertThat(dueno.getRole()).isEqualTo(UserRole.OWNER);
        assertThat(dueno.getBusinessId()).isEqualTo(registro.businessId());
        assertThat(dueno.getStaffId()).as("un dueño no es barbero").isNull();
    }

    @Test
    @DisplayName("guarda la contraseña hasheada, nunca en claro")
    void guardaLaContrasenaHasheada() {
        RegistrationResult registro = service.register(comando("hash-test", "hash@elcorte.co"));

        String guardado = users.findById(registro.ownerId()).orElseThrow().getPasswordHash();

        assertThat(guardado).isNotEqualTo("clave-segura-123");
        assertThat(passwordEncoder.matches("clave-segura-123", guardado))
                .as("el hash corresponde a la contraseña original")
                .isTrue();
    }

    @Test
    @DisplayName("asigna un codigo de referido propio a cada negocio")
    void asignaCodigoDeReferido() {
        Business uno = businesses.findById(
                service.register(comando("codigo-uno", "uno@elcorte.co")).businessId()).orElseThrow();
        Business otro = businesses.findById(
                service.register(comando("codigo-dos", "dos@elcorte.co")).businessId()).orElseThrow();

        assertThat(uno.getReferralCode()).isNotBlank().hasSize(8);
        assertThat(otro.getReferralCode()).isNotEqualTo(uno.getReferralCode());
    }

    @Test
    @DisplayName("rechaza un slug que ya esta tomado")
    void rechazaSlugRepetido() {
        service.register(comando("repetido", "primero@elcorte.co"));

        assertThatThrownBy(() -> service.register(comando("repetido", "segundo@elcorte.co")))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).code())
                .isEqualTo(ErrorCode.SLUG_TAKEN);
    }

    @Test
    @DisplayName("rechaza un correo que ya tiene cuenta")
    void rechazaCorreoRepetido() {
        service.register(comando("negocio-uno", "mismo@elcorte.co"));

        assertThatThrownBy(() -> service.register(comando("negocio-dos", "mismo@elcorte.co")))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).code())
                .isEqualTo(ErrorCode.EMAIL_TAKEN);
    }
}
