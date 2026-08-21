package com.guardao.backend.staff;

import com.guardao.backend.business.LocationService;
import com.guardao.backend.shared.error.ApiException;
import com.guardao.backend.shared.error.ErrorCode;
import java.util.List;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * GUA-31 — Servicios que ofrece una sede: corte, barba, tinturado.
 *
 * Se llama ServiceCatalogService y no ServiceService por lo obvio, y ademas
 * porque el nombre corto choca: la entidad de este paquete tambien se llama
 * Service. Por eso la anotacion de abajo va con su paquete completo — un
 * "import org.springframework.stereotype.Service" en este archivo taparia la
 * entidad y nada de aqui compilaria. Es el unico archivo del modulo donde
 * pasa; si aparece otro que necesite las dos, misma solucion.
 *
 * Nada que ver con el modulo catalog, que es la tienda de productos (Etapa 7).
 *
 * Mismo esquema que StaffService: la sede se verifica primero contra el
 * negocio del token, y a partir de ahi se filtra por sede (ADR-004).
 */
@org.springframework.stereotype.Service
public class ServiceCatalogService {

    private final ServiceRepository servicios;
    private final LocationService sedes;

    public ServiceCatalogService(ServiceRepository servicios, LocationService sedes) {
        this.servicios = servicios;
        this.sedes = sedes;
    }

    @Transactional
    public ServiceResponse create(UUID locationId, ServiceRequest peticion) {
        verificarSede(locationId);

        Service servicio = new Service(locationId, peticion.name(),
                peticion.price(), peticion.durationMin());

        return ServiceResponse.from(servicios.save(servicio));
    }

    @Transactional(readOnly = true)
    public List<ServiceResponse> list(UUID locationId, boolean soloActivos) {
        verificarSede(locationId);

        List<Service> encontrados = soloActivos
                ? servicios.findByLocationIdAndActiveTrueOrderByNameAsc(locationId)
                : servicios.findByLocationIdOrderByNameAsc(locationId);

        return encontrados.stream().map(ServiceResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ServiceResponse get(UUID locationId, UUID serviceId) {
        return ServiceResponse.from(buscar(locationId, serviceId));
    }

    /**
     * Editar cambia el precio y la duracion de aqui en adelante. Las citas ya
     * agendadas no se tocan: cada una guarda su propia copia de ambos
     * (ADR-010), asi que subir el corte de $25.000 a $28.000 no le cambia el
     * precio a quien ya reservo.
     */
    @Transactional
    public ServiceResponse update(UUID locationId, UUID serviceId, ServiceRequest peticion) {
        Service servicio = buscar(locationId, serviceId);

        servicio.setName(peticion.name());
        servicio.setPrice(peticion.price());
        servicio.setDurationMin(peticion.durationMin());

        return ServiceResponse.from(servicio);
    }

    /**
     * Desactiva el servicio en vez de borrarlo, por la misma razon que con los
     * barberos: las citas lo referencian con ON DELETE RESTRICT y el informe
     * de ingresos por servicio se apoya en esas filas.
     *
     * Deja de ofrecerse para reservar, y las habilidades que lo tenian
     * asignado (GUA-32) siguen ahi por si vuelve a activarse.
     */
    @Transactional
    public void deactivate(UUID locationId, UUID serviceId) {
        Service servicio = buscar(locationId, serviceId);
        servicio.setActive(false);
    }

    @Transactional
    public ServiceResponse reactivate(UUID locationId, UUID serviceId) {
        Service servicio = buscar(locationId, serviceId);
        servicio.setActive(true);

        return ServiceResponse.from(servicio);
    }

    private Service buscar(UUID locationId, UUID serviceId) {
        verificarSede(locationId);

        return servicios.findByIdAndLocationId(serviceId, locationId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND,
                        "Ese servicio no existe en esta sede"));
    }

    /** Falla con 404 si la sede no es del negocio del token. */
    private void verificarSede(UUID locationId) {
        sedes.get(locationId);
    }
}
