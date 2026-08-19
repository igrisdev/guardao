package com.guardao.backend.business;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * GUA-24 — Crea el super-admin interno al arrancar, si esta configurado.
 *
 * Los super-admin no se crean por endpoint, ni siquiera uno protegido: una
 * ruta capaz de fabricar usuarios con acceso a toda la plataforma es un
 * blanco permanente, y no hace falta, porque estas cuentas se crean una vez
 * por entorno y casi nunca cambian.
 *
 * Tampoco se crean con un INSERT a mano en la base, porque la contraseña debe
 * quedar hasheada con el mismo algoritmo que usa el login, y eso es incomodo
 * de hacer en SQL. Al pasar por aqui, se usa el mismo PasswordEncoder que el
 * resto de la aplicacion.
 *
 * Es idempotente: si el correo ya existe, no lo toca. Asi se puede dejar la
 * variable puesta en el entorno sin que cada reinicio intente recrear la
 * cuenta, y sin que un despliegue le devuelva la contraseña original a alguien
 * que ya la habia cambiado.
 */
@Component
@EnableConfigurationProperties(SuperAdminProperties.class)
public class SuperAdminSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SuperAdminSeeder.class);

    private final SuperAdminProperties properties;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    public SuperAdminSeeder(SuperAdminProperties properties,
            UserRepository users,
            PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.estaConfigurado()) {
            return;
        }

        properties.validar();

        if (users.existsByEmail(properties.email())) {
            log.info("El super-admin {} ya existe; no se toca", properties.email());
            return;
        }

        users.save(User.superAdmin(
                properties.email(), passwordEncoder.encode(properties.password())));

        // Se registra el correo pero jamas la contraseña, ni siquiera al crear
        log.info("Super-admin creado: {}. Quite la variable del entorno y cambie la clave "
                + "en el primer inicio de sesion", properties.email());
    }
}
