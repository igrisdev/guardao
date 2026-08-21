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
 * GUA-31 — Servicios de una sede.
 *
 * Misma forma que los barberos: la sede va en la ruta, el negocio sale del
 * token y nunca de la peticion (ADR-004).
 *
 * Consultar puede cualquiera del negocio: un barbero necesita el precio y la
 * duracion para agendar desde el mostrador. Cambiar el catalogo es del dueño.
 */
@RestController
@RequestMapping("/api/v1/locations/{locationId}/services")
@Tag(name = "Servicios", description = "Servicios que ofrece una sede")
@SecurityRequirement(name = "bearerAuth")
public class ServiceController {

    private final ServiceCatalogService servicios;

    public ServiceController(ServiceCatalogService servicios) {
        this.servicios = servicios;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER', 'STAFF')")
    @Operation(summary = "Lista los servicios de una sede")
    public List<ServiceResponse> list(@PathVariable UUID locationId,
            @RequestParam(name = "activos", defaultValue = "false") boolean soloActivos) {
        return servicios.list(locationId, soloActivos);
    }

    @GetMapping("/{serviceId}")
    @PreAuthorize("hasAnyRole('OWNER', 'STAFF')")
    @Operation(summary = "Consulta un servicio")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "El servicio"),
            @ApiResponse(responseCode = "404",
                    description = "No existe, es de otra sede, o la sede es de otro negocio")
    })
    public ServiceResponse get(@PathVariable UUID locationId, @PathVariable UUID serviceId) {
        return servicios.get(locationId, serviceId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('OWNER')")
    @Operation(summary = "Agrega un servicio a la sede",
            description = "El precio va en pesos enteros, sin decimales. La duracion en minutos, "
                    + "siempre multiplo de 30 (30, 60, 90...): la agenda se dibuja en bloques de "
                    + "media hora y una duracion suelta deja huecos que nadie puede ocupar.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "El servicio creado"),
            @ApiResponse(responseCode = "400",
                    description = "Falta un campo, el precio es negativo, o la duracion no es "
                            + "multiplo de 30")
    })
    public ServiceResponse create(@PathVariable UUID locationId,
            @Valid @RequestBody ServiceRequest peticion) {
        return servicios.create(locationId, peticion);
    }

    @PutMapping("/{serviceId}")
    @PreAuthorize("hasRole('OWNER')")
    @Operation(summary = "Edita el nombre, el precio o la duracion de un servicio",
            description = "El precio nuevo rige de aqui en adelante. Las citas ya agendadas "
                    + "conservan el que se pacto con el cliente (ADR-010).")
    public ServiceResponse update(@PathVariable UUID locationId, @PathVariable UUID serviceId,
            @Valid @RequestBody ServiceRequest peticion) {
        return servicios.update(locationId, serviceId, peticion);
    }

    @DeleteMapping("/{serviceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('OWNER')")
    @Operation(summary = "Retira un servicio del catalogo",
            description = "Lo desactiva, no lo borra: las citas que ya lo usaron lo referencian y "
                    + "el informe de ingresos por servicio se apoya en ellas. Deja de ofrecerse "
                    + "para reservar; se puede volver a activar.")
    public void deactivate(@PathVariable UUID locationId, @PathVariable UUID serviceId) {
        servicios.deactivate(locationId, serviceId);
    }

    @PostMapping("/{serviceId}/reactivate")
    @PreAuthorize("hasRole('OWNER')")
    @Operation(summary = "Vuelve a ofrecer un servicio retirado")
    public ServiceResponse reactivate(@PathVariable UUID locationId,
            @PathVariable UUID serviceId) {
        return servicios.reactivate(locationId, serviceId);
    }
}
