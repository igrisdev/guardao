package com.guardao.backend.staff;

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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * GUA-31 — Barberos de una sede.
 *
 * La sede va en la ruta y no en el cuerpo: asi cada peticion dice sobre que
 * sede opera, y esa sede se verifica contra el negocio del token antes de
 * cualquier otra cosa. El negocio nunca viaja en la peticion (ADR-004).
 *
 * Consultar puede cualquiera del negocio: un barbero necesita ver con quien
 * comparte sede. Crear, editar y dar de baja es del dueño, porque es
 * configuracion del negocio.
 */
@RestController
@RequestMapping("/api/v1/locations/{locationId}/staff")
@Tag(name = "Barberos", description = "Barberos de una sede")
@SecurityRequirement(name = "bearerAuth")
public class StaffController {

    private final StaffService barberos;

    public StaffController(StaffService barberos) {
        this.barberos = barberos;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER', 'STAFF')")
    @Operation(summary = "Lista los barberos de una sede")
    public List<StaffResponse> list(@PathVariable UUID locationId,
            @RequestParam(name = "activos", defaultValue = "false") boolean soloActivos) {
        return barberos.list(locationId, soloActivos);
    }

    @GetMapping("/{staffId}")
    @PreAuthorize("hasAnyRole('OWNER', 'STAFF')")
    @Operation(summary = "Consulta un barbero")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "El barbero"),
            @ApiResponse(responseCode = "404",
                    description = "No existe, es de otra sede, o la sede es de otro negocio. Se "
                            + "responde igual en los tres casos para no revelar que ese "
                            + "identificador existe")
    })
    public StaffResponse get(@PathVariable UUID locationId, @PathVariable UUID staffId) {
        return barberos.get(locationId, staffId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('OWNER')")
    @Operation(summary = "Agrega un barbero a la sede",
            description = "Crea solo la ficha del barbero. Darle acceso al sistema es aparte "
                    + "(GUA-37): hay barberos que nunca entran y su agenda la maneja el mostrador.")
    public StaffResponse create(@PathVariable UUID locationId,
            @Valid @RequestBody StaffRequest peticion) {
        return barberos.create(locationId, peticion);
    }

    @PutMapping("/{staffId}")
    @PreAuthorize("hasRole('OWNER')")
    @Operation(summary = "Edita el nombre de un barbero")
    public StaffResponse update(@PathVariable UUID locationId, @PathVariable UUID staffId,
            @Valid @RequestBody StaffRequest peticion) {
        return barberos.update(locationId, staffId, peticion);
    }

    @DeleteMapping("/{staffId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('OWNER')")
    @Operation(summary = "Da de baja a un barbero",
            description = "Lo desactiva, no lo borra: sus citas atendidas lo referencian y los "
                    + "informes por barbero se apoyan en ellas. Deja de aparecer para reservar y "
                    + "de contar para la disponibilidad; se puede volver a activar.")
    public void deactivate(@PathVariable UUID locationId, @PathVariable UUID staffId) {
        barberos.deactivate(locationId, staffId);
    }

    @PostMapping("/{staffId}/reactivate")
    @PreAuthorize("hasRole('OWNER')")
    @Operation(summary = "Vuelve a activar a un barbero dado de baja")
    public StaffResponse reactivate(@PathVariable UUID locationId, @PathVariable UUID staffId) {
        return barberos.reactivate(locationId, staffId);
    }
}
