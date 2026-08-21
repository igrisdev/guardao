package com.guardao.backend.staff;

import com.guardao.backend.shared.tenant.TenantContext;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * GUA-31 — Barbero de una sede.
 *
 * Cuelga de la sede y no del negocio: quien atiende en la sede del norte no
 * aparece en la agenda de la del sur, y el motor de disponibilidad (GUA-35)
 * cruza siempre sede + barbero.
 *
 * locationId se guarda como identificador y no como relacion @ManyToOne, por
 * la misma razon que en Location con su negocio: es la columna por la que se
 * filtra en cada consulta, y como relacion Hibernate traeria la sede completa
 * cada vez sin que nadie la pida.
 *
 * Un barbero no lleva correo ni clave aqui. Su login es una fila aparte en
 * app_user con role = 'STAFF' apuntando a este registro, y lo crea el dueño
 * cuando hace falta (GUA-37): hay barberos que nunca entran al sistema y su
 * agenda la maneja el mostrador.
 */
@Entity
@Table(name = "staff")
// GUA-22 — Aislamiento por negocio. La tabla no tiene business_id, asi que la
// pertenencia se resuelve a traves de la sede: un barbero es de este negocio
// si su sede lo es. Sin esto, un findById con el identificador de un barbero
// ajeno devolveria la fila (ADR-004).
@Filter(name = TenantContext.FILTER_NAME,
        condition = "location_id in (select sede.id from location sede"
                + " where sede.business_id = :businessId)")
public class Staff {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * Sede donde atiende. No se puede cambiar: mover un barbero de sede
     * dejaria sus citas pasadas colgando de una sede en la que nunca estuvo,
     * y sus horarios y bloqueos apuntando a la anterior. Para eso se crea el
     * barbero en la sede nueva y se desactiva el de la vieja.
     */
    @Column(name = "location_id", nullable = false, updatable = false)
    private UUID locationId;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    /**
     * Un barbero que se va se desactiva; no se borra, porque sus citas
     * atendidas son historial y los informes por barbero dejarian de cuadrar.
     */
    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Requerido por JPA. */
    protected Staff() {
    }

    public Staff(UUID locationId, String name) {
        this.locationId = locationId;
        this.name = name;
    }

    public UUID getId() {
        return id;
    }

    public UUID getLocationId() {
        return locationId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Staff otro)) {
            return false;
        }
        return id != null && id.equals(otro.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
