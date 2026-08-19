package com.guardao.backend.business;

import static org.assertj.core.api.Assertions.assertThat;

import com.guardao.backend.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * GUA-21 — Verificacion de credenciales.
 *
 * Todos los caminos de rechazo deben verse iguales desde afuera: un Optional
 * vacio, sin distinguir el motivo.
 */
@Transactional
class UserAccountServiceTest extends IntegrationTest {

    private static final String CLAVE = "clave-segura-123";

    @Autowired
    private UserAccountService service;

    @Autowired
    private BusinessRegistrationService registro;

    @Autowired
    private UserRepository users;

    private RegistrationResult cuenta;

    @BeforeEach
    void registrarBarberia() {
        cuenta = registro.register(new RegistrationCommand(
                "Barberia El Corte", "cuenta-el-corte", "Sede Centro",
                null, "Cali", "cuenta@elcorte.co", CLAVE));
    }

    @Test
    @DisplayName("con credenciales correctas devuelve la cuenta con su negocio y rol")
    void credencialesCorrectasDevuelvenLaCuenta() {
        UserAccount encontrada = service.authenticate("cuenta@elcorte.co", CLAVE).orElseThrow();

        assertThat(encontrada.userId()).isEqualTo(cuenta.ownerId());
        assertThat(encontrada.businessId()).isEqualTo(cuenta.businessId());
        assertThat(encontrada.businessSlug()).isEqualTo("cuenta-el-corte");
        assertThat(encontrada.role()).isEqualTo(UserRole.OWNER);
        assertThat(encontrada.staffId()).isNull();
    }

    @Test
    @DisplayName("con la contraseña equivocada no devuelve nada")
    void contrasenaEquivocadaNoDevuelveNada() {
        assertThat(service.authenticate("cuenta@elcorte.co", "otra-clave-cualquiera")).isEmpty();
    }

    @Test
    @DisplayName("con un correo que no existe tampoco devuelve nada")
    void correoInexistenteNoDevuelveNada() {
        assertThat(service.authenticate("nadie@elcorte.co", CLAVE)).isEmpty();
    }

    @Test
    @DisplayName("una cuenta desactivada no puede entrar aunque la clave sea correcta")
    void cuentaDesactivadaNoPuedeEntrar() {
        User usuario = users.findById(cuenta.ownerId()).orElseThrow();
        usuario.setActive(false);
        users.saveAndFlush(usuario);

        assertThat(service.authenticate("cuenta@elcorte.co", CLAVE)).isEmpty();
        assertThat(service.findActiveAccount(cuenta.ownerId())).isEmpty();
    }

    @Test
    @DisplayName("recarga la cuenta por identificador para renovar la sesion")
    void recargaLaCuentaPorIdentificador() {
        assertThat(service.findActiveAccount(cuenta.ownerId()))
                .get()
                .satisfies(activa -> assertThat(activa.role()).isEqualTo(UserRole.OWNER));
    }
}
