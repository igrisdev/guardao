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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * GUA-34 — Dias libres, vacaciones y bloqueos puntuales de un barbero.
 *
 * Cuelgan del barbero dentro de su sede, que es como se verifica que sean del
 * negocio del token (ADR-004).
 *
 * Consultar puede cualquiera del negocio: el barbero necesita ver sus propios
 * bloqueos. Crearlos y quitarlos es del dueño, porque un barbero que pudiera
 * bloquearse solo se quitaria de la agenda sin que nadie se entere.
 */
@RestController
@RequestMapping("/api/v1/locations/{locationId}/staff/{staffId}/blocks")
@Tag(name = "Bloqueos", description = "Dias libres, vacaciones y ausencias de un barbero")
@SecurityRequirement(name = "bearerAuth")
public class BlockController {

    private final BlockService bloqueos;

    public BlockController(BlockService bloqueos) {
        this.bloqueos = bloqueos;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER', 'STAFF')")
    @Operation(summary = "Lista los bloqueos de un barbero")
    public List<BlockResponse> list(@PathVariable UUID locationId, @PathVariable UUID staffId) {
        return bloqueos.list(locationId, staffId);
    }

    @GetMapping("/{blockId}")
    @PreAuthorize("hasAnyRole('OWNER', 'STAFF')")
    @Operation(summary = "Consulta un bloqueo")
    public BlockResponse get(@PathVariable UUID locationId, @PathVariable UUID staffId,
            @PathVariable UUID blockId) {
        return bloqueos.get(locationId, staffId, blockId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('OWNER')")
    @Operation(summary = "Bloquea un rato de la agenda de un barbero",
            description = "Las fechas van con zona horaria. El rato bloqueado deja de ofrecerse "
                    + "en la disponibilidad de ese barbero (GUA-35).")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "El bloqueo creado"),
            @ApiResponse(responseCode = "400", description = "El fin no es posterior al inicio"),
            @ApiResponse(responseCode = "404", description = "Ese barbero no existe en esta sede")
    })
    public BlockResponse create(@PathVariable UUID locationId, @PathVariable UUID staffId,
            @Valid @RequestBody BlockRequest peticion) {
        return bloqueos.create(locationId, staffId, peticion);
    }

    @PutMapping("/{blockId}")
    @PreAuthorize("hasRole('OWNER')")
    @Operation(summary = "Cambia las fechas o el motivo de un bloqueo")
    public BlockResponse update(@PathVariable UUID locationId, @PathVariable UUID staffId,
            @PathVariable UUID blockId, @Valid @RequestBody BlockRequest peticion) {
        return bloqueos.update(locationId, staffId, blockId, peticion);
    }

    @DeleteMapping("/{blockId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('OWNER')")
    @Operation(summary = "Quita un bloqueo",
            description = "Este si se borra de verdad, a diferencia de barberos y servicios: no "
                    + "hay historial que conservar y ese rato debe volver a estar libre.")
    public void delete(@PathVariable UUID locationId, @PathVariable UUID staffId,
            @PathVariable UUID blockId) {
        bloqueos.delete(locationId, staffId, blockId);
    }
}
