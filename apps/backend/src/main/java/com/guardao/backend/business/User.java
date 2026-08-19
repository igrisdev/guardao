package com.guardao.backend.business;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import com.guardao.backend.shared.tenant.TenantContext;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * GUA-19 — Usuario que inicia sesion. La tabla se llama app_user porque user
 * es palabra reservada en Postgres.
 *
 * staffId solo se llena cuando el rol es STAFF, y la base lo exige con el
 * CHECK app_user_staff_only_for_staff_role: un STAFF sin staff_id no podria
 * ver su propia agenda, y un OWNER con staff_id enreda los permisos. Por eso
 * el rol y el staff se asignan juntos, nunca por separado.
 *
 * Se guarda el hash de la contraseña, jamas la contraseña.
 *
 * businessId es nulo unicamente en los SUPER_ADMIN: son personal interno de
 * Guardao, que es la plataforma y no una barberia (GUA-24). La base lo exige
 * con el CHECK app_user_business_only_for_tenant_roles, en los dos sentidos.
 */
@Entity
@Table(name = "app_user")
// GUA-22 — Solo se ven las filas del negocio de la peticion. El filtro se
// enciende solo (TenantFilterActivator); aqui no hay nada que recordar.
@Filter(name = TenantContext.FILTER_NAME, condition = "business_id = :businessId")
public class User {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Nulo solo en los SUPER_ADMIN, que no pertenecen a ninguna barberia. */
    @Column(name = "business_id", updatable = false)
    private UUID businessId;

    /**
     * Barbero al que pertenece este login. Identificador y no relacion: la
     * entidad Staff todavia no existe (llega en Etapa 2) y de todos modos
     * vive en otro modulo (ADR-002).
     */
    @Column(name = "staff_id")
    private UUID staffId;

    @Column(name = "email", nullable = false, length = 180)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 120)
    private String passwordHash;

    // STRING y no ORDINAL: la columna guarda el nombre del rol, y con ORDINAL
    // reordenar el enum reescribiria en silencio el rol de todo el mundo
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private UserRole role;

    /** Un empleado que se va se desactiva; no se borra, porque atendio citas. */
    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Requerido por JPA. */
    protected User() {
    }

    /** Crea un usuario que no es barbero: OWNER o SUPER_ADMIN. */
    public User(UUID businessId, String email, String passwordHash, UserRole role) {
        this.businessId = businessId;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
    }

    /**
     * Crea un super-admin interno de Guardao. Va sin negocio a proposito: no
     * es empleado de ninguna barberia, y la base rechaza la fila si trae uno.
     *
     * No hay ningun endpoint que llegue aqui: estos usuarios se crean solo
     * por el seed de arranque (GUA-24).
     */
    public static User superAdmin(String email, String passwordHash) {
        User user = new User();
        user.email = email;
        user.passwordHash = passwordHash;
        user.role = UserRole.SUPER_ADMIN;
        return user;
    }

    /**
     * Crea el login de un barbero. Es un constructor aparte para que el
     * staffId no se pueda olvidar: sin el, la base rechaza la fila.
     */
    public static User forStaff(UUID businessId, String email, String passwordHash, UUID staffId) {
        User user = new User(businessId, email, passwordHash, UserRole.STAFF);
        user.staffId = staffId;
        return user;
    }

    public UUID getId() {
        return id;
    }

    public UUID getBusinessId() {
        return businessId;
    }

    public UUID getStaffId() {
        return staffId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public UserRole getRole() {
        return role;
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
        if (!(other instanceof User otro)) {
            return false;
        }
        return id != null && id.equals(otro.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
