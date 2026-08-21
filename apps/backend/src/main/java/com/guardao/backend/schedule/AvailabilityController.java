package com.guardao.backend.schedule;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * GUA-35 — Huecos libres para agendar.
 *
 * Es el endpoint mas importante de la etapa: de aqui sale lo que ve el
 * cliente cuando va a reservar.
 *
 * Por ahora esta protegido, para el dashboard. La version publica —sin token,
 * resolviendo el negocio por el slug de la URL— es de la Etapa 4 y usara este
 * mismo calculo, no otro: dos motores de disponibilidad terminan
 * respondiendose distinto y el cliente veria un hueco que el mostrador no.
 */
@RestController
@RequestMapping("/api/v1/locations/{locationId}/availability")
@Tag(name = "Disponibilidad", description = "Huecos libres para agendar")
@SecurityRequirement(name = "bearerAuth")
public class AvailabilityController {

    private final AvailabilityService disponibilidad;

    public AvailabilityController(AvailabilityService disponibilidad) {
        this.disponibilidad = disponibilidad;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER', 'STAFF')")
    @Operation(summary = "Huecos libres para un servicio en un rango de fechas",
            description = "Cruza el horario de la sede, el horario propio del barbero, sus "
                    + "bloqueos y sus citas ya agendadas. Los huecos se calculan con la duracion "
                    + "del servicio elegido: para uno de 90 minutos no se ofrece un hueco de 60. "
                    + "Solo se consideran los barberos activos que tengan asignada la habilidad "
                    + "de ese servicio (GUA-32).")
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "Un elemento por dia del rango. Los dias sin huecos vienen con "
                            + "la lista vacia, no se omiten"),
            @ApiResponse(responseCode = "400",
                    description = "El rango esta al reves o pasa de 62 dias"),
            @ApiResponse(responseCode = "404",
                    description = "El servicio no existe en esta sede, o la sede es de otro negocio")
    })
    public AvailabilityResponse find(
            @PathVariable UUID locationId,

            @Parameter(description = "Servicio que se quiere agendar; su duracion es la que "
                    + "determina el tamaño de los huecos", required = true)
            @RequestParam UUID serviceId,

            @Parameter(description = "Primer dia del rango, en formato AAAA-MM-DD", required = true)
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,

            @Parameter(description = "Ultimo dia del rango, incluido", required = true)
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,

            @Parameter(description = "Opcional: limita la respuesta a un barbero. Sin el, se "
                    + "devuelven los huecos de todos los que saben hacer el servicio")
            @RequestParam(required = false) UUID staffId) {

        return disponibilidad.find(locationId, serviceId, from, to, staffId);
    }
}
