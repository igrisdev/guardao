package com.guardao.backend.shared.error;

import java.time.OffsetDateTime;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * GUA-12 — Forma unica de un error en la API (Tech Spec 5.1).
 *
 * <pre>
 * {
 *   "code": "SLOT_NOT_AVAILABLE",
 *   "message": "Ese horario ya no esta disponible",
 *   "details": { "field": "scheduledAt" },
 *   "timestamp": "2026-08-17T10:30:00-05:00"
 * }
 * </pre>
 *
 * Se llama ApiError y no ErrorResponse para no confundirla con la interfaz
 * ErrorResponse de Spring, que es otra cosa.
 *
 * details se omite cuando es nulo: el frontend no tiene que distinguir entre
 * ausente y vacio.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        String code,
        String message,
        Map<String, Object> details,
        OffsetDateTime timestamp) {

    public static ApiError of(ErrorCode code, String message,
            Map<String, Object> details, OffsetDateTime now) {
        return new ApiError(code.name(), message, details, now);
    }
}
