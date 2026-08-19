package com.guardao.backend.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * GUA-21 — Renovacion de la sesion.
 *
 * El token va en el cuerpo y no en el header Authorization: ese header lo
 * intercepta el filtro de token portador, que solo acepta tokens de acceso y
 * rechazaria este antes de llegar al controlador.
 */
public record RefreshRequest(

        @NotBlank(message = "El token de refresco es obligatorio")
        String refreshToken) {
}
