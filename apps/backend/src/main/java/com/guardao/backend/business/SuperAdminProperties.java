package com.guardao.backend.business;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * GUA-24 — Credenciales del super-admin que se crea al arrancar.
 *
 * Se leen de guardao.super-admin.*, y en staging y produccion vienen de
 * variables de entorno configuradas en Coolify. Nunca se escriben en un
 * archivo del repositorio: seria publicar la contraseña del personal interno
 * en el historial de git, donde no hay forma de borrarla de verdad.
 *
 * Si no estan definidas, el seed no hace nada. Es el caso normal: solo se
 * definen la primera vez que se levanta un entorno.
 */
@ConfigurationProperties(prefix = "guardao.super-admin")
public record SuperAdminProperties(String email, String password) {

    /** Longitud minima para no crear una cuenta interna con una clave debil. */
    private static final int LARGO_MINIMO_CLAVE = 12;

    public boolean estaConfigurado() {
        return email != null && !email.isBlank()
                && password != null && !password.isBlank();
    }

    /**
     * Se valida al arrancar y no al usar: si alguien configura el seed con una
     * contraseña de cuatro letras, conviene que el despliegue falle en ese
     * momento y no que quede una cuenta con acceso a todo mal protegida.
     */
    public void validar() {
        if (password.length() < LARGO_MINIMO_CLAVE) {
            throw new IllegalStateException(
                    "guardao.super-admin.password debe tener al menos "
                            + LARGO_MINIMO_CLAVE + " caracteres");
        }
    }
}
