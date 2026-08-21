package com.guardao.backend.schedule;

import com.guardao.backend.shared.tenant.TenantScopedRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * GUA-34 — Acceso a los bloqueos de un barbero.
 *
 * La consulta por rango usa "empieza antes de que termine el rango y termina
 * despues de que empiece" y no "esta contenido en el rango". La diferencia
 * importa: unas vacaciones de dos semanas no estan contenidas en el martes que
 * se esta consultando, pero lo tapan entero. Con la condicion ingenua ese
 * martes apareceria disponible.
 */
public interface BlockRepository extends TenantScopedRepository<Block, UUID> {

    List<Block> findByStaffIdOrderByStartAtAsc(UUID staffId);

    Optional<Block> findByIdAndStaffId(UUID id, UUID staffId);

    /** Los bloqueos que pisan el rango, aunque empiecen antes o terminen despues. */
    List<Block> findByStaffIdAndStartAtLessThanAndEndAtGreaterThanOrderByStartAtAsc(
            UUID staffId, Instant hasta, Instant desde);
}
