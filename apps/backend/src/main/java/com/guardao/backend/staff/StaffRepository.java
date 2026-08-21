package com.guardao.backend.staff;

import com.guardao.backend.shared.tenant.TenantScopedRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * GUA-31 — Acceso a barberos.
 *
 * Todas las consultas piden la sede ademas del identificador propio. No es
 * redundante: buscar solo por id permitiria alcanzar el barbero de otra sede
 * —o de otra barberia— con solo cambiar el UUID de la URL. Con el par, la
 * respuesta es vacia y el servicio responde 404.
 *
 * La sede que llega aqui ya viene verificada contra el negocio del token, asi
 * que filtrar por ella alcanza para cerrar el paso entre barberias (ADR-004).
 */
public interface StaffRepository extends TenantScopedRepository<Staff, UUID> {

    List<Staff> findByLocationIdOrderByNameAsc(UUID locationId);

    List<Staff> findByLocationIdAndActiveTrueOrderByNameAsc(UUID locationId);

    /** Usar esta en vez de findById en todo lo que venga de una peticion. */
    Optional<Staff> findByIdAndLocationId(UUID id, UUID locationId);

    /**
     * GUA-32 — Los barberos de la sede cuyo id esta en la lista.
     *
     * La sede sigue en la consulta aunque los ids ya vengan acotados: es lo
     * que impide que un id colado en la lista traiga un barbero de otra sede.
     */
    List<Staff> findByLocationIdAndIdInOrderByNameAsc(UUID locationId, Collection<UUID> ids);
}
