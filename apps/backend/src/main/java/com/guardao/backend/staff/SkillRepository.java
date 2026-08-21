package com.guardao.backend.staff;

import com.guardao.backend.shared.tenant.TenantScopedRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * GUA-32 — Acceso a habilidades.
 *
 * Las consultas van por el barbero o por el servicio, nunca por el id propio
 * de la habilidad: nadie la referencia por su identificador, se busca siempre
 * "que sabe hacer este barbero" o "quien sabe hacer este servicio".
 *
 * Extiende TenantScopedRepository como todos los demas. No es formalidad: un
 * repositorio que herede de JpaRepository directamente corre sus consultas
 * derivadas sin transaccion, y por lo tanto sin el filtro de negocio (ver
 * TenantScopedRepository).
 */
public interface SkillRepository extends TenantScopedRepository<Skill, UUID> {

    List<Skill> findByStaffId(UUID staffId);

    List<Skill> findByServiceId(UUID serviceId);

    Optional<Skill> findByStaffIdAndServiceId(UUID staffId, UUID serviceId);

    boolean existsByStaffIdAndServiceId(UUID staffId, UUID serviceId);
}
