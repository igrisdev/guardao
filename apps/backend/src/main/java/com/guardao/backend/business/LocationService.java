package com.guardao.backend.business;

import com.guardao.backend.shared.error.ApiException;
import com.guardao.backend.shared.error.ErrorCode;
import com.guardao.backend.shared.tenant.TenantContext;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * GUA-25 — Sedes de un negocio.
 *
 * El negocio se toma de TenantContext, que lo resolvio el filtro a partir del
 * token (GUA-22), y no de CurrentUser: asi este modulo no depende de la capa
 * de autenticacion (ADR-002).
 *
 * Las consultas pasan ademas el businessId explicitamente. Es redundante con
 * el filtro automatico, y esta bien que lo sea: el aislamiento es la pieza
 * mas critica del sistema y conviene que se vea en el propio codigo, no solo
 * en la configuracion (ADR-004).
 */
@Service
public class LocationService {

    private final LocationRepository locations;

    public LocationService(LocationRepository locations) {
        this.locations = locations;
    }

    @Transactional
    public LocationResponse create(LocationRequest peticion) {
        Location sede = new Location(TenantContext.require(), peticion.name());
        sede.setAddress(peticion.address());
        sede.setCity(peticion.city());

        return LocationResponse.from(locations.save(sede));
    }

    @Transactional(readOnly = true)
    public List<LocationResponse> list(boolean soloActivas) {
        UUID businessId = TenantContext.require();

        List<Location> encontradas = soloActivas
                ? locations.findByBusinessIdAndActiveTrue(businessId)
                : locations.findByBusinessId(businessId);

        return encontradas.stream().map(LocationResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public LocationResponse get(UUID id) {
        return LocationResponse.from(buscar(id));
    }

    @Transactional
    public LocationResponse update(UUID id, LocationRequest peticion) {
        Location sede = buscar(id);

        sede.setName(peticion.name());
        sede.setAddress(peticion.address());
        sede.setCity(peticion.city());

        return LocationResponse.from(sede);
    }

    /**
     * Desactiva la sede en vez de borrarla.
     *
     * Borrarla de verdad se llevaria por delante su historial: staff,
     * servicios, horarios y citas cuelgan de ella con ON DELETE CASCADE, asi
     * que un DELETE eliminaria en silencio las citas ya atendidas y sus pagos.
     * Una sede que cierra deja de aparecer, pero lo que ocurrio en ella sigue
     * existiendo.
     */
    @Transactional
    public void deactivate(UUID id) {
        Location sede = buscar(id);

        if (!sede.isActive()) {
            return;
        }

        // Sin ninguna sede activa, la pagina publica del negocio se queda sin
        // nada que ofrecer y nadie puede reservar
        if (locations.countByBusinessIdAndActiveTrue(TenantContext.require()) <= 1) {
            throw new ApiException(ErrorCode.LAST_ACTIVE_LOCATION);
        }

        sede.setActive(false);
    }

    @Transactional
    public LocationResponse reactivate(UUID id) {
        Location sede = buscar(id);
        sede.setActive(true);

        return LocationResponse.from(sede);
    }

    private Location buscar(UUID id) {
        return locations.findByIdAndBusinessId(id, TenantContext.require())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Esa sede no existe"));
    }
}
