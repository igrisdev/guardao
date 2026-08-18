package com.guardao.backend.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * GUA-21 — Credenciales de inicio de sesion.
 *
 * Aqui no se valida formato de correo ni largo de contraseña, al reves que
 * en el registro: un 400 diciendo "el correo no tiene formato valido"
 * distinguiria un intento de otro, y la respuesta a unas credenciales que no
 * sirven debe ser siempre la misma.
 */
public record LoginRequest(

        @NotBlank(message = "El correo es obligatorio")
        String email,

        @NotBlank(message = "La contraseña es obligatoria")
        String password) {
}
