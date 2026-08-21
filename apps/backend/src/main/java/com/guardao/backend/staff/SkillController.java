package com.guardao.backend.staff;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * GUA-32 — Habilidades: que barbero sabe hacer que servicio.
 *
 * Las rutas cuelgan de la sede igual que barberos y servicios, porque una
 * habilidad solo tiene sentido entre dos cosas de la misma sede.
 *
 * Asignar y revocar van con PUT y DELETE sobre la misma direccion, y no con
 * POST, porque describen un estado y no una accion: "este barbero sabe hacer
 * este servicio" o no lo sabe. Repetir cualquiera de los dos deja el mismo
 * resultado, que es justo lo que necesita una pantalla de casillas (GUA-38).
 *
 * Consultar puede cualquiera del negocio: es lo que permite saber a quien
 * ofrecerle una cita. Cambiar quien sabe que es del dueño.
 */
@RestController
@RequestMapping("/api/v1/locations/{locationId}")
@Tag(name = "Habilidades", description = "Que barbero sabe hacer que servicio")
@SecurityRequirement(name = "bearerAuth")
public class SkillController {

    private final SkillService habilidades;

    public SkillController(SkillService habilidades) {
        this.habilidades = habilidades;
    }

    @GetMapping("/staff/{staffId}/skills")
    @PreAuthorize("hasAnyRole('OWNER', 'STAFF')")
    @Operation(summary = "Servicios que sabe hacer un barbero")
    public List<ServiceResponse> servicesOfStaff(@PathVariable UUID locationId,
            @PathVariable UUID staffId) {
        return habilidades.servicesOfStaff(locationId, staffId);
    }

    @GetMapping("/services/{serviceId}/staff")
    @PreAuthorize("hasAnyRole('OWNER', 'STAFF')")
    @Operation(summary = "Barberos que atienden un servicio",
            description = "Es la consulta que alimenta el selector de barbero: cuando el cliente "
                    + "elige un servicio, solo se le ofrecen los que saben hacerlo.")
    public List<StaffResponse> staffOfService(@PathVariable UUID locationId,
            @PathVariable UUID serviceId) {
        return habilidades.staffOfService(locationId, serviceId);
    }

    @PutMapping("/staff/{staffId}/skills/{serviceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('OWNER')")
    @Operation(summary = "Asigna una habilidad a un barbero",
            description = "Idempotente: asignarla de nuevo responde igual y no duplica nada.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "El barbero sabe hacer ese servicio"),
            @ApiResponse(responseCode = "404",
                    description = "El barbero o el servicio no existen en esta sede. Tambien "
                            + "responde asi cuando existen pero son de sedes distintas: un "
                            + "barbero no puede atender un servicio donde no trabaja")
    })
    public void assign(@PathVariable UUID locationId, @PathVariable UUID staffId,
            @PathVariable UUID serviceId) {
        habilidades.assign(locationId, staffId, serviceId);
    }

    @DeleteMapping("/staff/{staffId}/skills/{serviceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('OWNER')")
    @Operation(summary = "Quita una habilidad a un barbero",
            description = "Idempotente: quitarla cuando ya no la tenia responde igual.")
    public void revoke(@PathVariable UUID locationId, @PathVariable UUID staffId,
            @PathVariable UUID serviceId) {
        habilidades.revoke(locationId, staffId, serviceId);
    }
}
