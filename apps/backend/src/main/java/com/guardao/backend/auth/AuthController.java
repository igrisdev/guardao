package com.guardao.backend.auth;

import com.guardao.backend.business.BusinessRegistrationService;
import com.guardao.backend.business.RegistrationCommand;
import com.guardao.backend.business.RegistrationResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * GUA-20 — Entrada a la plataforma.
 *
 * Vive bajo /api/v1/auth, que es publico por lista blanca en SecurityConfig:
 * quien se registra todavia no tiene token con que autenticarse.
 *
 * El controlador no crea nada por su cuenta. Le pide al modulo business que
 * de el alta dentro de su transaccion, y solo despues emite la sesion
 * (ADR-002). Login y refresco se suman aqui en GUA-21.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Autenticacion", description = "Registro de barberias e inicio de sesion")
public class AuthController {

    private final BusinessRegistrationService registrationService;
    private final TokenService tokenService;
    private final JwtProperties jwtProperties;

    public AuthController(BusinessRegistrationService registrationService,
            TokenService tokenService,
            JwtProperties jwtProperties) {
        this.registrationService = registrationService;
        this.tokenService = tokenService;
        this.jwtProperties = jwtProperties;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registra una barberia",
            description = "Crea el negocio, su primera sede y el usuario dueño en una sola "
                    + "operacion, y devuelve la sesion ya iniciada.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Barberia creada y sesion iniciada"),
            @ApiResponse(responseCode = "400", description = "Datos invalidos"),
            @ApiResponse(responseCode = "409", description = "La URL publica o el correo ya estan en uso")
    })
    public SessionResponse register(@Valid @RequestBody RegisterRequest request) {
        RegistrationResult registro = registrationService.register(new RegistrationCommand(
                request.businessName(),
                request.slug(),
                request.locationName(),
                request.address(),
                request.city(),
                request.email(),
                request.password()));

        // El dueño no tiene barbero asociado: staffId va nulo
        AuthenticatedUser usuario = new AuthenticatedUser(
                registro.ownerId(),
                registro.businessId(),
                AuthenticatedUser.Role.valueOf(registro.role().name()),
                null);

        return new SessionResponse(
                tokenService.createAccessToken(usuario),
                tokenService.createRefreshToken(usuario.userId()),
                SessionResponse.BEARER,
                jwtProperties.accessTokenMinutes() * 60L,
                usuario.userId(),
                usuario.businessId(),
                registro.slug(),
                usuario.role().name());
    }
}
