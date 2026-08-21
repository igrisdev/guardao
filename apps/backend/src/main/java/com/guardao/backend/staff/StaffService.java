package com.guardao.backend.staff;

import com.guardao.backend.business.LocationService;
import com.guardao.backend.shared.error.ApiException;
import com.guardao.backend.shared.error.ErrorCode;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * GUA-31 — Barberos de una sede.
 *
 * Cada operacion empieza verificando la sede contra el negocio del token. Ese
 * es el punto donde se cierra el paso entre barberias: una vez comprobado que
 * la sede es del negocio de quien pregunta, filtrar por sede alcanza para que
 * nada ajeno se cuele (ADR-004).
 *
 * La comprobacion se hace llamando a LocationService y no a LocationRepository
 * directamente: los modulos se hablan por sus servicios publicos (ADR-002).
 * Su get ya responde 404 cuando la sede no existe o es de otra barberia, que
 * es exactamente la respuesta que corresponde aqui.
 */
@Service
public class StaffService {

    private final StaffRepository barberos;
    private final LocationService sedes;

    public StaffService(StaffRepository barberos, LocationService sedes) {
        this.barberos = barberos;
        this.sedes = sedes;
    }

    @Transactional
    public StaffResponse create(UUID locationId, StaffRequest peticion) {
        verificarSede(locationId);

        Staff barbero = new Staff(locationId, peticion.name());

        return StaffResponse.from(barberos.save(barbero));
    }

    @Transactional(readOnly = true)
    public List<StaffResponse> list(UUID locationId, boolean soloActivos) {
        verificarSede(locationId);

        List<Staff> encontrados = soloActivos
                ? barberos.findByLocationIdAndActiveTrueOrderByNameAsc(locationId)
                : barberos.findByLocationIdOrderByNameAsc(locationId);

        return encontrados.stream().map(StaffResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public StaffResponse get(UUID locationId, UUID staffId) {
        return StaffResponse.from(buscar(locationId, staffId));
    }

    @Transactional
    public StaffResponse update(UUID locationId, UUID staffId, StaffRequest peticion) {
        Staff barbero = buscar(locationId, staffId);
        barbero.setName(peticion.name());

        return StaffResponse.from(barbero);
    }

    /**
     * Desactiva al barbero en vez de borrarlo.
     *
     * No es una preferencia de estilo, es lo unico que la base permite: sus
     * citas lo referencian con ON DELETE RESTRICT, asi que en cuanto atendio
     * una sola, un DELETE se rechaza. Y si tiene login propio, borrarlo
     * dejaria su usuario con staff_id nulo, que es justo lo que prohibe el
     * CHECK app_user_staff_only_for_staff_role.
     *
     * Un barbero desactivado deja de aparecer para reservar y de contar para
     * la disponibilidad, pero sus citas atendidas siguen en el historial y en
     * los informes por barbero.
     */
    @Transactional
    public void deactivate(UUID locationId, UUID staffId) {
        Staff barbero = buscar(locationId, staffId);
        barbero.setActive(false);
    }

    @Transactional
    public StaffResponse reactivate(UUID locationId, UUID staffId) {
        Staff barbero = buscar(locationId, staffId);
        barbero.setActive(true);

        return StaffResponse.from(barbero);
    }

    private Staff buscar(UUID locationId, UUID staffId) {
        verificarSede(locationId);

        return barberos.findByIdAndLocationId(staffId, locationId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND,
                        "Ese barbero no existe en esta sede"));
    }

    /** Falla con 404 si la sede no es del negocio del token. */
    private void verificarSede(UUID locationId) {
        sedes.get(locationId);
    }
}
