package com.guardao.backend.business;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * GUA-19 — Negocio suscrito a Guardao. Es la raiz del aislamiento
 * multi-tenant: todo lo demas cuelga de aqui (ADR-004).
 *
 * Las columnas de tema (theme_preset, theme_colors) existen en la tabla pero
 * no se mapean todavia: son de la pagina publica (Etapa 4) y theme_colors es
 * jsonb, que necesita un mapeo aparte. Al no declararlas, Hibernate las deja
 * fuera del INSERT y la base aplica sus valores por defecto.
 */
@Entity
@Table(name = "business")
public class Business {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    /** Identifica al negocio en su link publico: guardao.com/book/{slug}. Unico. */
    @Column(name = "slug", nullable = false, length = 80)
    private String slug;

    /** Vertical del negocio. Hoy solo barberias; discotecas y restaurantes despues. */
    @Column(name = "type", nullable = false, length = 40)
    private String type = "BARBERSHOP";

    /** Codigo que este negocio entrega para referir a otros. Unico. */
    @Column(name = "referral_code", nullable = false, length = 20)
    private String referralCode;

    /**
     * Negocio que lo refirio, si alguno. Se guarda el identificador y no una
     * relacion: nadie necesita navegar al referidor, y evita arrastrar otro
     * negocio en cada consulta. El calculo de comisiones es GUA-8 de Etapa 8.
     */
    @Column(name = "referred_by_id")
    private UUID referredById;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // La tabla no tiene trigger para updated_at, asi que lo mantiene Hibernate
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Requerido por JPA. */
    protected Business() {
    }

    public Business(String name, String slug, String referralCode) {
        this.name = name;
        this.slug = slug;
        this.referralCode = referralCode;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getReferralCode() {
        return referralCode;
    }

    public void setReferralCode(String referralCode) {
        this.referralCode = referralCode;
    }

    public UUID getReferredById() {
        return referredById;
    }

    public void setReferredById(UUID referredById) {
        this.referredById = referredById;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    // Dos entidades son la misma si comparten identificador. Mientras el id
    // sea nulo (aun sin guardar) solo es igual a si misma.
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Business otro)) {
            return false;
        }
        return id != null && id.equals(otro.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
