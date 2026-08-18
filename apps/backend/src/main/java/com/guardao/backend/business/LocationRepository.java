package com.guardao.backend.business;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * GUA-19 — Acceso a sedes.
 *
 * Las consultas piden el businessId ademas del identificador propio. No es
 * redundante: buscar solo por id permitiria que alguien pidiera la sede de
 * otro negocio y la obtuviera (ADR-004). Con el par, la respuesta es vacia.
 */
public interface LocationRepository extends JpaRepository<Location, UUID> {

    List<Location> findByBusinessId(UUID businessId);

    List<Location> findByBusinessIdAndActiveTrue(UUID businessId);

    /** Usar esta en vez de findById en todo lo que venga de una peticion. */
    Optional<Location> findByIdAndBusinessId(UUID id, UUID businessId);
}
