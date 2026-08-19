package com.guardao.backend.business;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * GUA-23 — Cuerpo para crear el acceso de un barbero.
 *
 * El negocio no viaja aqui: sale del token del dueño (ADR-004). Solo se pide
 * a que barbero pertenece el acceso y con que correo y clave entra.
 *
 * @param staffId  barbero al que se le crea el login. Debe existir y ser del
 *                 negocio del dueño; si no, se responde 404 sin decir si el
 *                 identificador existe en otra barberia.
 * @param password minimo 8 caracteres. El tope de 72 es el de BCrypt, que
 *                 ignora lo que pase de 72 bytes (mismo criterio del registro).
 */
public record StaffAccountRequest(

        @NotNull(message = "El barbero es obligatorio")
        UUID staffId,

        @NotBlank(message = "El correo es obligatorio")
        @Email(message = "El correo no tiene un formato valido")
        @Size(max = 180, message = "El correo no puede pasar de 180 caracteres")
        String email,

        @NotBlank(message = "La contraseña es obligatoria")
        @Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres")
        String password) {
}
