package com.guardao.backend.staff;

import com.guardao.backend.shared.tenant.TenantScopedRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * GUA-31 — Acceso a servicios.
 *
 * Misma regla que en StaffRepository: la sede va en cada consulta, nunca se
 * busca solo por id.
 */
public interface ServiceRepository extends TenantScopedRepository<Service, UUID> {

    List<Service> findByLocationIdOrderByNameAsc(UUID locationId);

    List<Service> findByLocationIdAndActiveTrueOrderByNameAsc(UUID locationId);

    /** Usar esta en vez de findById en todo lo que venga de una peticion. */
    Optional<Service> findByIdAndLocationId(UUID id, UUID locationId);

    /** GUA-32 — Los servicios de la sede cuyo id esta en la lista. */
    List<Service> findByLocationIdAndIdInOrderByNameAsc(UUID locationId, Collection<UUID> ids);
}
