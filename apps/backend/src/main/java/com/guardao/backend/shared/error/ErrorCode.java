package com.guardao.backend.shared.error;

import org.springframework.http.HttpStatus;

/**
 * GUA-12 — Catalogo de errores de negocio (Tech Spec 5.1).
 *
 * El frontend decide que mostrar segun el codigo, nunca segun el texto del
 * mensaje: los mensajes cambian, los codigos no.
 *
 * Cada codigo lleva su HTTP asociado para que un mismo error no salga con
 * 409 en un endpoint y 400 en otro.
 */
public enum ErrorCode {

    // --- Reservas ---
    SLOT_NOT_AVAILABLE(HttpStatus.CONFLICT,
            "Ese horario ya no esta disponible"),
    ADVANCE_PAYMENT_REQUIRED(HttpStatus.PAYMENT_REQUIRED,
            "Se requiere pago por adelantado para reservar"),
    NOT_ASSIGNED_STAFF(HttpStatus.FORBIDDEN,
            "Solo el barbero asignado puede completar esta cita"),

    // --- Negocio ---
    SLUG_TAKEN(HttpStatus.CONFLICT,
            "Ese nombre de URL ya esta en uso"),
    // Va separado de SLUG_TAKEN para que el formulario de registro sepa cual
    // de los dos campos marcar en rojo (GUA-20)
    EMAIL_TAKEN(HttpStatus.CONFLICT,
            "Ese correo ya tiene una cuenta"),
    // Un negocio sin ninguna sede activa no puede recibir reservas: su pagina
    // publica se queda sin nada que ofrecer (GUA-25)
    LAST_ACTIVE_LOCATION(HttpStatus.CONFLICT,
            "No puede desactivar la unica sede activa del negocio"),
    // Se responde igual para un barbero inexistente que para uno de otro
    // negocio: distinguirlos revelaria que ese identificador existe en otra
    // barberia (GUA-23)
    STAFF_NOT_FOUND(HttpStatus.NOT_FOUND,
            "Ese barbero no existe en su negocio"),
    // Cada barbero tiene un solo acceso: dos logins para el mismo barbero
    // enredarian quien completa su cita (GUA-23)
    STAFF_ALREADY_HAS_LOGIN(HttpStatus.CONFLICT,
            "Ese barbero ya tiene un acceso creado"),

    // --- Pagos ---
    INVALID_WEBHOOK_SIGNATURE(HttpStatus.UNAUTHORIZED,
            "Firma del webhook invalida"),

    // --- Transversales ---
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST,
            "Hay campos con datos invalidos"),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED,
            "Se requiere iniciar sesion"),
    // Un solo codigo para correo inexistente y clave equivocada: distinguirlos
    // le confirmaria a un atacante que correos tienen cuenta (GUA-21)
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED,
            "Correo o contraseña incorrectos"),
    // Separado del anterior para que el frontend sepa que debe mandar al login
    // en vez de mostrar el formulario con un error de campo
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED,
            "La sesion expiro, vuelva a iniciar sesion"),
    FORBIDDEN(HttpStatus.FORBIDDEN,
            "No tiene permisos para esta accion"),
    NOT_FOUND(HttpStatus.NOT_FOUND,
            "El recurso solicitado no existe"),
    CONFLICT(HttpStatus.CONFLICT,
            "La operacion entra en conflicto con el estado actual"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR,
            "Ocurrio un error inesperado");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
