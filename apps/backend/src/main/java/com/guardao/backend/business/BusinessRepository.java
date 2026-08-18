package com.guardao.backend.business;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * GUA-19 — Acceso a negocios.
 *
 * No lleva filtro por tenant: el negocio ES el tenant. Los repositorios que
 * si lo necesitan reciben el businessId del usuario autenticado (ADR-004).
 */
public interface BusinessRepository extends JpaRepository<Business, UUID> {

    /** Resuelve el negocio de la pagina publica de reservas (Etapa 4). */
    Optional<Business> findBySlug(String slug);

    /** El slug es unico: se valida antes de registrar, para no chocar con la restriccion. */
    boolean existsBySlug(String slug);

    /** Identifica a quien refirio, al registrarse con un codigo (Etapa 8). */
    Optional<Business> findByReferralCode(String referralCode);

    boolean existsByReferralCode(String referralCode);
}
