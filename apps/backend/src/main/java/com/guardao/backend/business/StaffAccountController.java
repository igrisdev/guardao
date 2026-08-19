package com.guardao.backend.business;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * GUA-23 — Acceso de los barberos.
 *
 * Ruta protegida: exige token y solo el dueño puede crear estos accesos, que
 * son configuracion del negocio. Un barbero no crea barberos.
 *
 * El negocio sale del token, nunca del cuerpo: no hay forma de crearle el
 * acceso a un barbero de otra barberia (ADR-004). El barbero se identifica
 * por su staff_id, que debe existir y ser de este negocio.
 */
@RestController
@RequestMapping("/api/v1/staff-accounts")
@Tag(name = "Accesos de barberos", description = "Usuarios STAFF vinculados a un barbero")
@SecurityRequirement(name = "bearerAuth")
public class StaffAccountController {

    private final StaffAccountService staffAccounts;

    public StaffAccountController(StaffAccountService staffAccounts) {
        this.staffAccounts = staffAccounts;
    }

    @PostMapping
    @PreAuthorize("hasRole('OWNER')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crea el acceso de un barbero",
            description = "El dueño le da acceso a un barbero: un usuario con rol STAFF atado a su "
                    + "registro de barbero. Con ese usuario el barbero inicia sesion.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "El acceso creado"),
            @ApiResponse(responseCode = "400", description = "Datos invalidos"),
            @ApiResponse(responseCode = "403", description = "Solo el dueño puede crear accesos"),
            @ApiResponse(responseCode = "404",
                    description = "El barbero no existe, o pertenece a otro negocio. Se responde "
                            + "igual en ambos casos para no revelar que ese identificador existe"),
            @ApiResponse(responseCode = "409",
                    description = "El barbero ya tiene acceso, o el correo ya esta en uso")
    })
    public StaffAccountResponse create(@Valid @RequestBody StaffAccountRequest peticion) {
        return staffAccounts.create(peticion);
    }
}
