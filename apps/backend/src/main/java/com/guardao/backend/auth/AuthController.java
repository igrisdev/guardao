package com.guardao.backend.auth;

import com.guardao.backend.business.BusinessRegistrationService;
import com.guardao.backend.business.RegistrationCommand;
import com.guardao.backend.business.RegistrationResult;
import com.guardao.backend.business.UserAccount;
import com.guardao.backend.business.UserAccountService;
import com.guardao.backend.shared.error.ApiException;
import com.guardao.backend.shared.error.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * GUA-20, GUA-21 — Entrada a la plataforma: registro, login y refresco.
 *
 * Vive bajo /api/v1/auth, publico por lista blanca en SecurityConfig: quien
 * llega aqui todavia no tiene con que autenticarse.
 *
 * El controlador no consulta repositorios. Le pide al modulo business que de
 * el alta o verifique las credenciales, y solo despues emite la sesion
 * (ADR-002).
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Autenticacion", description = "Registro de barberias e inicio de sesion")
public class AuthController {

    private final BusinessRegistrationService registrationService;
    private final UserAccountService accountService;
    private final TokenService tokenService;
    private final JwtDecoder jwtDecoder;
    private final JwtProperties jwtProperties;

    public AuthController(BusinessRegistrationService registrationService,
            UserAccountService accountService,
            TokenService tokenService,
            JwtDecoder jwtDecoder,
            JwtProperties jwtProperties) {
        this.registrationService = registrationService;
        this.accountService = accountService;
        this.tokenService = tokenService;
        this.jwtDecoder = jwtDecoder;
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

        // El dueño recien creado no es barbero, por eso va sin staffId
        return sesionPara(new UserAccount(
                registro.ownerId(), registro.businessId(), registro.slug(), registro.role(), null));
    }

    @PostMapping("/login")
    @Operation(summary = "Inicia sesion",
            description = "Verifica las credenciales y devuelve los tokens de la sesion.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sesion iniciada"),
            @ApiResponse(responseCode = "401",
                    description = "Credenciales incorrectas. La respuesta es identica tanto si el "
                            + "correo no existe como si la contraseña esta mal.")
    })
    public SessionResponse login(@Valid @RequestBody LoginRequest request) {
        UserAccount cuenta = accountService.authenticate(request.email(), request.password())
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_CREDENTIALS));

        return sesionPara(cuenta);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Renueva la sesion",
            description = "Entrega un par de tokens nuevo a partir de un token de refresco vigente. "
                    + "El rol y el negocio se releen de la base, no se copian del token.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sesion renovada"),
            @ApiResponse(responseCode = "401",
                    description = "El token no es de refresco, caduco, o la cuenta ya no esta activa")
    })
    public SessionResponse refresh(@Valid @RequestBody RefreshRequest request) {
        UUID userId = usuarioDelTokenDeRefresco(request.refreshToken());

        // Se relee la cuenta: si al usuario lo dieron de baja o le cambiaron el
        // rol, surte efecto en este refresco y no cuando caduque el token
        UserAccount cuenta = accountService.findActiveAccount(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_REFRESH_TOKEN));

        return sesionPara(cuenta);
    }

    private UUID usuarioDelTokenDeRefresco(String token) {
        Jwt jwt;
        try {
            // Comprueba firma y vigencia; lo demas se revisa abajo
            jwt = jwtDecoder.decode(token);
        } catch (JwtException ex) {
            throw new ApiException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        if (!TokenService.isRefreshToken(jwt)) {
            throw new ApiException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new ApiException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
    }

    /** Unico lugar donde se arma una sesion, para que los tres endpoints devuelvan lo mismo. */
    private SessionResponse sesionPara(UserAccount cuenta) {
        AuthenticatedUser usuario = new AuthenticatedUser(
                cuenta.userId(),
                cuenta.businessId(),
                AuthenticatedUser.Role.valueOf(cuenta.role().name()),
                cuenta.staffId());

        return new SessionResponse(
                tokenService.createAccessToken(usuario),
                tokenService.createRefreshToken(usuario.userId()),
                SessionResponse.BEARER,
                jwtProperties.accessTokenMinutes() * 60L,
                usuario.userId(),
                usuario.businessId(),
                cuenta.businessSlug(),
                usuario.role().name());
    }
}
