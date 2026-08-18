package com.guardao.backend.business;

import com.guardao.backend.shared.error.ApiException;
import com.guardao.backend.shared.error.ErrorCode;
import java.security.SecureRandom;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * GUA-20 — Alta de una barberia en la plataforma.
 *
 * Crea el negocio, su primera sede y el usuario dueño en una sola
 * transaccion. Es todo o nada a proposito: un negocio sin dueño no permite
 * iniciar sesion, y un dueño sin sede no puede crear ni un servicio. Quedar
 * a mitad de camino dejaria una cuenta inutilizable que nadie puede reparar
 * desde la aplicacion.
 */
@Service
public class BusinessRegistrationService {

    /**
     * Alfabeto del codigo de referido. Sin O/0 ni I/1/L: el codigo se dicta
     * por telefono y se escribe a mano, y esos pares se confunden.
     */
    private static final String ALFABETO_CODIGO = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final int LARGO_CODIGO = 8;

    /**
     * Con 31^8 combinaciones un choque es rarisimo, pero el codigo es unico
     * en la base: si ocurre, se reintenta en vez de tumbar el registro.
     */
    private static final int INTENTOS_CODIGO = 5;

    private final BusinessRepository businesses;
    private final LocationRepository locations;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom random = new SecureRandom();

    public BusinessRegistrationService(BusinessRepository businesses,
            LocationRepository locations,
            UserRepository users,
            PasswordEncoder passwordEncoder) {
        this.businesses = businesses;
        this.locations = locations;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public RegistrationResult register(RegistrationCommand command) {
        // Se comprueba antes para poder decir cual de los dos campos falla.
        // Las restricciones de la base siguen ahi por si dos registros
        // simultaneos pasan ambos por aqui: entonces una de las dos
        // transacciones muere y el cliente recibe un 409.
        if (businesses.existsBySlug(command.slug())) {
            throw ApiException.of(ErrorCode.SLUG_TAKEN, "slug", command.slug());
        }
        if (users.existsByEmail(command.email())) {
            throw ApiException.of(ErrorCode.EMAIL_TAKEN, "email", command.email());
        }

        Business business = new Business(
                command.businessName(), command.slug(), generarCodigoReferido());
        businesses.save(business);

        Location location = new Location(business.getId(), command.locationName());
        location.setAddress(command.address());
        location.setCity(command.city());
        locations.save(location);

        User owner = new User(
                business.getId(),
                command.email(),
                passwordEncoder.encode(command.rawPassword()),
                UserRole.OWNER);
        users.save(owner);

        return new RegistrationResult(
                business.getId(), business.getSlug(), location.getId(), owner.getId(), owner.getRole());
    }

    private String generarCodigoReferido() {
        for (int intento = 0; intento < INTENTOS_CODIGO; intento++) {
            String codigo = codigoAleatorio();
            if (!businesses.existsByReferralCode(codigo)) {
                return codigo;
            }
        }
        // Cinco choques seguidos no es mala suerte, es una señal de que algo
        // anda mal con la generacion. Mejor fallar que entrar en un ciclo.
        throw new IllegalStateException(
                "No se pudo generar un codigo de referido unico en " + INTENTOS_CODIGO + " intentos");
    }

    private String codigoAleatorio() {
        StringBuilder codigo = new StringBuilder(LARGO_CODIGO);
        for (int i = 0; i < LARGO_CODIGO; i++) {
            codigo.append(ALFABETO_CODIGO.charAt(random.nextInt(ALFABETO_CODIGO.length())));
        }
        return codigo.toString();
    }
}
