package com.guardao.backend.shared.error;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * GUA-12 — Traduce cualquier excepcion a la forma unica de error.
 *
 * Sin esto, el frontend recibe la pagina de error por defecto de Spring en
 * unos casos y JSON en otros, y termina adivinando.
 *
 * OJO: los errores de autenticacion y autorizacion que ocurren en la cadena
 * de filtros (401 sin token, 403 por rol) NO pasan por aqui, porque saltan
 * antes de llegar al controlador. Los cubre SecurityErrorResponder, para que
 * salgan con este mismo formato.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Nombre de la restriccion EXCLUDE que impide la doble reserva (ADR-003). */
    private static final String NO_OVERLAP_CONSTRAINT = "appointment_no_overlap";

    private final ZoneId zone;

    public GlobalExceptionHandler(@Value("${guardao.timezone}") String timezone) {
        this.zone = ZoneId.of(timezone);
    }

    /** Reglas de negocio: el caso esperado. */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApi(ApiException ex) {
        return build(ex.code(), ex.getMessage(), ex.details());
    }

    /** @Valid fallido: se devuelve campo por campo para poder marcarlos en el formulario. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, Object> fields = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fields.putIfAbsent(error.getField(), error.getDefaultMessage());
        }

        return build(ErrorCode.VALIDATION_ERROR,
                ErrorCode.VALIDATION_ERROR.defaultMessage(),
                Map.of("fields", fields));
    }

    /**
     * Violaciones de restricciones de la base de datos.
     *
     * El caso importante: cuando dos clientes reservan el mismo horario a la
     * vez, la segunda transaccion choca contra appointment_no_overlap. Aqui
     * se traduce a SLOT_NOT_AVAILABLE, que es un 409 con sentido para el
     * usuario, en vez de un 500.
     *
     * Sin esta traduccion, la unica defensa real contra la doble reserva
     * (ADR-003) se le presentaria al cliente como "error del servidor".
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException ex) {
        String cause = ex.getMostSpecificCause().getMessage();

        if (cause != null && cause.contains(NO_OVERLAP_CONSTRAINT)) {
            log.info("Doble reserva evitada por la base de datos");
            return build(ErrorCode.SLOT_NOT_AVAILABLE,
                    ErrorCode.SLOT_NOT_AVAILABLE.defaultMessage(), null);
        }

        // Cualquier otra violacion es un dato duplicado o una FK rota.
        // Se registra completa, pero no se le devuelve al cliente: el
        // mensaje de Postgres revela nombres de tablas y columnas.
        log.warn("Violacion de integridad no mapeada", ex);
        return build(ErrorCode.CONFLICT, ErrorCode.CONFLICT.defaultMessage(), null);
    }

    /** @PreAuthorize rechazado dentro del controlador. */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex) {
        return build(ErrorCode.FORBIDDEN, ErrorCode.FORBIDDEN.defaultMessage(), null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(NoResourceFoundException ex) {
        return build(ErrorCode.NOT_FOUND, ErrorCode.NOT_FOUND.defaultMessage(), null);
    }

    /**
     * Red de seguridad. Se registra el detalle completo en el log y se
     * devuelve un mensaje generico: una traza revela estructura interna.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex) {
        log.error("Error no controlado", ex);
        return build(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.defaultMessage(), null);
    }

    private ResponseEntity<ApiError> build(ErrorCode code, String message,
            Map<String, Object> details) {
        // Truncado a segundos: el contrato documentado en el Tech Spec 5.1 no
        // lleva fracciones, y los nanosegundos de la JVM no le sirven a nadie
        // que consuma la API.
        OffsetDateTime now = OffsetDateTime.now(zone).truncatedTo(ChronoUnit.SECONDS);

        return ResponseEntity.status(code.status())
                .body(ApiError.of(code, message, details, now));
    }
}
