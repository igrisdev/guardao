package com.guardao.backend.shared.error;

import java.util.Map;

/**
 * GUA-12 — Excepcion de negocio.
 *
 * Es la unica forma en que un servicio deberia rechazar una operacion por
 * una regla del dominio. Lanzar RuntimeException suelta hace que el error
 * salga como 500 y el frontend no pueda distinguir "el horario se ocupo" de
 * "el servidor se cayo".
 */
public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final transient Map<String, Object> details;

    public ApiException(ErrorCode code) {
        this(code, code.defaultMessage(), null);
    }

    public ApiException(ErrorCode code, String message) {
        this(code, message, null);
    }

    public ApiException(ErrorCode code, String message, Map<String, Object> details) {
        super(message);
        this.code = code;
        this.details = details;
    }

    /** Atajo para el caso mas comun: senalar el campo que fallo. */
    public static ApiException of(ErrorCode code, String field, Object value) {
        return new ApiException(code, code.defaultMessage(),
                Map.of("field", field, "value", String.valueOf(value)));
    }

    public ErrorCode code() {
        return code;
    }

    public Map<String, Object> details() {
        return details;
    }
}
