package com.guardao.backend.staff;

import com.guardao.backend.business.LocationService;
import com.guardao.backend.shared.error.ApiException;
import com.guardao.backend.shared.error.ErrorCode;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * GUA-32 — Habilidades: que barbero sabe hacer que servicio.
 *
 * Lo que hace util a esta tabla es lo que permite despues: cuando el cliente
 * elija "tinturado" en la pagina publica, solo se le ofrecen los barberos que
 * lo saben hacer (Etapa 4), y el motor de disponibilidad (GUA-35) solo mira
 * las agendas de esos.
 *
 * El barbero y el servicio tienen que ser de la MISMA sede. No es un capricho
 * de coherencia: un barbero de la sede del norte asignado a un servicio de la
 * del sur apareceria como disponible en un sitio donde no trabaja.
 */
@Service
public class SkillService {

    private final SkillRepository habilidades;
    private final StaffRepository barberos;
    private final ServiceRepository servicios;
    private final LocationService sedes;

    public SkillService(SkillRepository habilidades, StaffRepository barberos,
            ServiceRepository servicios, LocationService sedes) {
        this.habilidades = habilidades;
        this.barberos = barberos;
        this.servicios = servicios;
        this.sedes = sedes;
    }

    /**
     * Asignar es idempotente: hacerlo dos veces deja una sola habilidad y
     * responde igual. La interfaz marca y desmarca casillas (GUA-38), y con
     * dos pestanas abiertas es normal que la misma marca llegue dos veces;
     * responder 409 obligaria al frontend a distinguir un error real de un
     * "ya estaba", cuando el resultado que pedia ya se cumple.
     *
     * La base lo respalda con skill_staff_service_unique, por si dos
     * peticiones llegan a la vez y ambas ven que no existia.
     */
    @Transactional
    public void assign(UUID locationId, UUID staffId, UUID serviceId) {
        verificarQueAmbosSonDeLaSede(locationId, staffId, serviceId);

        if (habilidades.existsByStaffIdAndServiceId(staffId, serviceId)) {
            return;
        }

        habilidades.save(new Skill(staffId, serviceId));
    }

    /**
     * Revocar tambien es idempotente: quitar algo que ya no esta deja el
     * mismo resultado, que es lo que promete un DELETE.
     */
    @Transactional
    public void revoke(UUID locationId, UUID staffId, UUID serviceId) {
        verificarQueAmbosSonDeLaSede(locationId, staffId, serviceId);

        habilidades.findByStaffIdAndServiceId(staffId, serviceId)
                .ifPresent(habilidades::delete);
    }

    /** Los servicios que sabe hacer un barbero. */
    @Transactional(readOnly = true)
    public List<ServiceResponse> servicesOfStaff(UUID locationId, UUID staffId) {
        exigirBarberoDeLaSede(locationId, staffId);

        List<UUID> ids = habilidades.findByStaffId(staffId).stream()
                .map(Skill::getServiceId)
                .toList();

        if (ids.isEmpty()) {
            return List.of();
        }

        return servicios.findByLocationIdAndIdInOrderByNameAsc(locationId, ids).stream()
                .map(ServiceResponse::from)
                .toList();
    }

    /**
     * Los barberos que atienden un servicio. Es la consulta que pide el
     * criterio de aceptacion del ticket, y la que despues alimenta el selector
     * de barbero de la pagina publica.
     */
    @Transactional(readOnly = true)
    public List<StaffResponse> staffOfService(UUID locationId, UUID serviceId) {
        exigirServicioDeLaSede(locationId, serviceId);

        List<UUID> ids = habilidades.findByServiceId(serviceId).stream()
                .map(Skill::getStaffId)
                .toList();

        if (ids.isEmpty()) {
            return List.of();
        }

        return barberos.findByLocationIdAndIdInOrderByNameAsc(locationId, ids).stream()
                .map(StaffResponse::from)
                .toList();
    }

    private void verificarQueAmbosSonDeLaSede(UUID locationId, UUID staffId, UUID serviceId) {
        exigirBarberoDeLaSede(locationId, staffId);
        exigirServicioDeLaSede(locationId, serviceId);
    }

    /**
     * Fallan con 404 en vez de devolver la fila: aqui solo interesa que
     * existan y sean de esta sede. Devolver la entidad obligaria a nombrar el
     * tipo Service, que en este archivo esta tapado por la anotacion @Service
     * de Spring (ver ServiceCatalogService).
     */
    private void exigirBarberoDeLaSede(UUID locationId, UUID staffId) {
        sedes.get(locationId);

        if (barberos.findByIdAndLocationId(staffId, locationId).isEmpty()) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Ese barbero no existe en esta sede");
        }
    }

    private void exigirServicioDeLaSede(UUID locationId, UUID serviceId) {
        sedes.get(locationId);

        if (servicios.findByIdAndLocationId(serviceId, locationId).isEmpty()) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Ese servicio no existe en esta sede");
        }
    }
}
