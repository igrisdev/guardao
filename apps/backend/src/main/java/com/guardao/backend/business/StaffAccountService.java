package com.guardao.backend.business;

import com.guardao.backend.shared.error.ApiException;
import com.guardao.backend.shared.error.ErrorCode;
import com.guardao.backend.shared.tenant.TenantContext;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * GUA-23 — Creacion del acceso de un barbero.
 *
 * El dueño le crea a un barbero un usuario con rol STAFF cuyo staff_id apunta
 * a su registro de Staff. Ese vinculo es lo que despues permite exigir que
 * solo el barbero asignado complete su propia cita.
 *
 * Sobre el barbero: la entidad Staff y su CRUD son de otro ticket (GUA-31) y
 * de otra persona, asi que aqui no se modela Staff. Solo se necesita una cosa
 * de esa tabla —que el barbero exista y sea de este negocio— y se resuelve con
 * una consulta de solo lectura. Cuando exista la entidad Staff, esta
 * comprobacion puede mudarse alli sin cambiar el contrato del endpoint.
 *
 * La consulta se hace por JDBC y no por el repositorio con filtro de negocio
 * porque la tabla staff no cuelga de business sino de location, y no lleva el
 * filtro multi-tenant. Se une por location y se pasa el businessId explicito,
 * que es justo lo que impide que un dueño cree el acceso de un barbero ajeno.
 */
@Service
public class StaffAccountService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbc;

    public StaffAccountService(UserRepository users,
            PasswordEncoder passwordEncoder,
            JdbcTemplate jdbc) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jdbc = jdbc;
    }

    @Transactional
    public StaffAccountResponse create(StaffAccountRequest peticion) {
        UUID businessId = TenantContext.require();

        if (!barberoEsDelNegocio(peticion.staffId(), businessId)) {
            throw new ApiException(ErrorCode.STAFF_NOT_FOUND);
        }

        // Un barbero tiene un solo acceso. Se mira dentro del negocio, que es
        // el alcance del filtro; el barbero ya se valido como propio arriba.
        if (users.findByStaffId(peticion.staffId()).isPresent()) {
            throw new ApiException(ErrorCode.STAFF_ALREADY_HAS_LOGIN);
        }

        // El correo es unico en toda la plataforma, no solo en el negocio: se
        // atrapa aqui para devolver un error claro en vez del de la base.
        if (users.existsByEmail(peticion.email())) {
            throw ApiException.of(ErrorCode.EMAIL_TAKEN, "email", peticion.email());
        }

        User barbero = User.forStaff(
                businessId,
                peticion.email(),
                passwordEncoder.encode(peticion.password()),
                peticion.staffId());

        return StaffAccountResponse.from(users.save(barbero));
    }

    /**
     * Verdadero solo si el barbero existe y pertenece a una sede de este
     * negocio. Pasar el businessId explicito es lo que cierra el aislamiento:
     * el staff_id de otra barberia simplemente no encaja.
     */
    private boolean barberoEsDelNegocio(UUID staffId, UUID businessId) {
        Boolean existe = jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1
                    FROM staff s
                    JOIN location l ON l.id = s.location_id
                    WHERE s.id = ? AND l.business_id = ?
                )
                """, Boolean.class, staffId, businessId);

        return Boolean.TRUE.equals(existe);
    }
}
