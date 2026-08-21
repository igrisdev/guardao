package com.guardao.backend.schedule;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * GUA-33 — Horario de la sede y horario propio de cada barbero.
 *
 * Dos direcciones para la misma idea, porque son dos cosas distintas: el
 * horario de la sede dice cuando esta abierto el local, el del barbero cuando
 * viene a trabajar el. El segundo vale dentro del primero.
 *
 * Se guarda con PUT y la semana entera, no con POST por franja. El porque
 * esta en ScheduleRequest: las reglas del horario son del conjunto.
 *
 * Consultar puede cualquiera del negocio, porque un barbero necesita ver su
 * propio horario. Cambiarlo es del dueño.
 */
@RestController
@RequestMapping("/api/v1/locations/{locationId}")
@Tag(name = "Horarios", description = "Horario semanal de la sede y de cada barbero")
@SecurityRequirement(name = "bearerAuth")
public class ScheduleController {

    private final ScheduleService horarios;

    public ScheduleController(ScheduleService horarios) {
        this.horarios = horarios;
    }

    @GetMapping("/schedule")
    @PreAuthorize("hasAnyRole('OWNER', 'STAFF')")
    @Operation(summary = "Horario general de la sede",
            description = "Las franjas de toda la semana, ordenadas por dia y hora. Un dia que no "
                    + "aparece es un dia cerrado.")
    public List<ScheduleSlotResponse> locationSchedule(@PathVariable UUID locationId) {
        return horarios.locationSchedule(locationId);
    }

    @PutMapping("/schedule")
    @PreAuthorize("hasRole('OWNER')")
    @Operation(summary = "Reemplaza el horario general de la sede",
            description = "Se manda la semana completa y sustituye a la anterior. Un dia puede "
                    + "tener varias franjas (jornada partida), siempre que no se crucen.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "El horario que quedo guardado"),
            @ApiResponse(responseCode = "400",
                    description = "Una hora fuera de la rejilla de media hora, o un cierre "
                            + "anterior a su apertura"),
            @ApiResponse(responseCode = "409",
                    description = "Dos franjas del mismo dia se cruzan")
    })
    public List<ScheduleSlotResponse> replaceLocationSchedule(@PathVariable UUID locationId,
            @Valid @RequestBody ScheduleRequest peticion) {
        return horarios.replaceLocationSchedule(locationId, peticion);
    }

    @GetMapping("/staff/{staffId}/schedule")
    @PreAuthorize("hasAnyRole('OWNER', 'STAFF')")
    @Operation(summary = "Horario propio de un barbero",
            description = "Vacio significa que no tiene horario propio y se rige por el de la sede.")
    public List<ScheduleSlotResponse> staffSchedule(@PathVariable UUID locationId,
            @PathVariable UUID staffId) {
        return horarios.staffSchedule(locationId, staffId);
    }

    @PutMapping("/staff/{staffId}/schedule")
    @PreAuthorize("hasRole('OWNER')")
    @Operation(summary = "Reemplaza el horario propio de un barbero",
            description = "Vale dentro del horario de la sede: si el barbero declara de 7 a 19 y "
                    + "la sede abre de 8 a 18, atiende de 8 a 18. No se rechaza al guardarlo "
                    + "porque el horario de la sede puede cambiar despues.")
    public List<ScheduleSlotResponse> replaceStaffSchedule(@PathVariable UUID locationId,
            @PathVariable UUID staffId, @Valid @RequestBody ScheduleRequest peticion) {
        return horarios.replaceStaffSchedule(locationId, staffId, peticion);
    }

    @DeleteMapping("/staff/{staffId}/schedule")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('OWNER')")
    @Operation(summary = "Quita el horario propio de un barbero",
            description = "Vuelve a regirse por el de la sede. No es lo mismo que guardarle una "
                    + "semana vacia, que seria un barbero que no trabaja ningun dia.")
    public void clearStaffSchedule(@PathVariable UUID locationId, @PathVariable UUID staffId) {
        horarios.clearStaffSchedule(locationId, staffId);
    }
}
