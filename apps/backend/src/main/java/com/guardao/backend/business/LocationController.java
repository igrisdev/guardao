package com.guardao.backend.business;

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
 * GUA-25 — Sedes del negocio.
 *
 * Ruta protegida: no esta en la lista blanca de SecurityConfig, asi que exige
 * token. El negocio sale de ese token y nunca del cuerpo ni de la URL, de
 * modo que no hay forma de pedir ni crear la sede de otra barberia
 * (ADR-004).
 *
 * Consultar puede cualquiera del negocio, porque un barbero necesita saber en
 * que sedes trabaja. Crear, editar y desactivar es solo del dueño: son
 * decisiones de configuracion del negocio.
 */
@RestController
@RequestMapping("/api/v1/locations")
@Tag(name = "Sedes", description = "Sedes de una barberia")
@SecurityRequirement(name = "bearerAuth")
public class LocationController {

    private final LocationService locations;

    public LocationController(LocationService locations) {
        this.locations = locations;
    }

    @GetMapping
    @Operation(summary = "Lista las sedes del negocio")
    public List<LocationResponse> list(
            @RequestParam(name = "activas", defaultValue = "false") boolean soloActivas) {
        return locations.list(soloActivas);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consulta una sede")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "La sede"),
            @ApiResponse(responseCode = "404",
                    description = "No existe, o pertenece a otro negocio. Se responde igual en "
                            + "ambos casos para no revelar que ese identificador existe")
    })
    public LocationResponse get(@PathVariable UUID id) {
        return locations.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('OWNER')")
    @Operation(summary = "Crea una sede")
    public LocationResponse create(@Valid @RequestBody LocationRequest peticion) {
        return locations.create(peticion);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('OWNER')")
    @Operation(summary = "Edita el nombre, la direccion o la ciudad de una sede")
    public LocationResponse update(@PathVariable UUID id,
            @Valid @RequestBody LocationRequest peticion) {
        return locations.update(id, peticion);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('OWNER')")
    @Operation(summary = "Cierra una sede",
            description = "La desactiva, no la borra: staff, servicios, horarios y citas cuelgan "
                    + "de ella, y un borrado real se llevaria por delante el historial. Deja de "
                    + "aparecer para reservar y se puede volver a abrir.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Sede cerrada"),
            @ApiResponse(responseCode = "409",
                    description = "Es la unica sede activa; el negocio se quedaria sin poder recibir reservas"),
            @ApiResponse(responseCode = "404", description = "No existe, o pertenece a otro negocio")
    })
    public void deactivate(@PathVariable UUID id) {
        locations.deactivate(id);
    }

    @PostMapping("/{id}/reactivate")
    @PreAuthorize("hasRole('OWNER')")
    @Operation(summary = "Vuelve a abrir una sede cerrada")
    public LocationResponse reactivate(@PathVariable UUID id) {
        return locations.reactivate(id);
    }
}
