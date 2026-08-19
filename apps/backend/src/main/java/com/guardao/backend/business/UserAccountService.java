package com.guardao.backend.business;

import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * GUA-21 — Consulta de cuentas para iniciar y renovar sesion.
 *
 * La verificacion de la contraseña vive aqui, junto a la entidad que guarda
 * el hash, y no en el modulo auth: asi el hash no cruza la frontera del
 * modulo (ADR-002).
 *
 * Todos los rechazos devuelven un Optional vacio, sin decir por que. Quien
 * llama no puede distinguir "no existe ese correo" de "la contraseña esta
 * mal" ni de "la cuenta esta desactivada", que es justo lo que se busca:
 * responder distinto seria confirmarle a un atacante que un correo tiene
 * cuenta en la plataforma.
 */
@Service
public class UserAccountService {

    private final UserRepository users;
    private final BusinessRepository businesses;
    private final PasswordEncoder passwordEncoder;

    /**
     * Hash de una contraseña que nadie conoce. Se compara contra el cuando
     * el correo no existe, para que la respuesta tarde lo mismo que un
     * intento fallido de verdad. Sin esto, un correo inexistente responderia
     * notoriamente mas rapido, porque BCrypt es lento a proposito, y ese
     * tiempo de mas revela que cuentas existen.
     */
    private final String hashSenuelo;

    public UserAccountService(UserRepository users,
            BusinessRepository businesses,
            PasswordEncoder passwordEncoder) {
        this.users = users;
        this.businesses = businesses;
        this.passwordEncoder = passwordEncoder;
        this.hashSenuelo = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    /** Devuelve la cuenta solo si el correo existe, la clave coincide y esta activa. */
    @Transactional(readOnly = true)
    public Optional<UserAccount> authenticate(String email, String rawPassword) {
        Optional<User> encontrado = users.findByEmail(email);

        if (encontrado.isEmpty()) {
            passwordEncoder.matches(rawPassword, hashSenuelo);
            return Optional.empty();
        }

        User user = encontrado.get();
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            return Optional.empty();
        }
        if (!user.isActive()) {
            return Optional.empty();
        }

        return Optional.of(toAccount(user));
    }

    /**
     * Recarga la cuenta al renovar la sesion. Se vuelve a la base a proposito
     * en vez de confiar en lo que traiga el token: asi un cambio de rol o una
     * baja surten efecto en el siguiente refresco, sin esperar a que caduque.
     */
    @Transactional(readOnly = true)
    public Optional<UserAccount> findActiveAccount(UUID userId) {
        return users.findById(userId)
                .filter(User::isActive)
                .map(this::toAccount);
    }

    private UserAccount toAccount(User user) {
        return new UserAccount(
                user.getId(), user.getBusinessId(), slugDelNegocio(user), user.getRole(),
                user.getStaffId());
    }

    /** Nulo para los SUPER_ADMIN: no pertenecen a ninguna barberia (GUA-24). */
    private String slugDelNegocio(User user) {
        if (user.getBusinessId() == null) {
            return null;
        }

        return businesses.findById(user.getBusinessId())
                .map(Business::getSlug)
                .orElseThrow(() -> new IllegalStateException(
                        "El usuario " + user.getId() + " apunta a un negocio que no existe"));
    }
}
