package com.guardao.backend.business;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * GUA-19 — Acceso a usuarios.
 *
 * findByEmail no filtra por negocio a proposito: en el login todavia no se
 * sabe de que negocio es quien escribe, y por eso el correo es unico en toda
 * la plataforma. El resto de consultas si exige el businessId (ADR-004).
 */
public interface UserRepository extends JpaRepository<User, UUID> {

    /** Punto de entrada del login (GUA-20). */
    Optional<User> findByEmail(String email);

    /** Evita llegar a la restriccion de unicidad con un mensaje de base de datos. */
    boolean existsByEmail(String email);

    Optional<User> findByIdAndBusinessId(UUID id, UUID businessId);

    List<User> findByBusinessId(UUID businessId);

    /** Recupera el login de un barbero para saber si ya tiene acceso creado. */
    Optional<User> findByStaffId(UUID staffId);
}
