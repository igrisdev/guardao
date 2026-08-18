package com.guardao.backend.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * GUA-20 — Cuerpo del registro de una barberia.
 *
 * Los limites de tamaño son los mismos de las columnas: asi un dato muy
 * largo se devuelve como error de validacion con el campo señalado, y no
 * como un error de base de datos.
 *
 * @param slug     va en la URL publica (guardao.com/book/{slug}), por eso se
 *                 restringe a minusculas, numeros y guiones simples.
 * @param password minimo 8 caracteres. El tope de 72 no es capricho: BCrypt
 *                 ignora lo que pase de 72 bytes, asi que aceptar mas seria
 *                 prometer una seguridad que no se cumple.
 */
public record RegisterRequest(

        @NotBlank(message = "El nombre del negocio es obligatorio")
        @Size(max = 120, message = "El nombre no puede pasar de 120 caracteres")
        String businessName,

        @NotBlank(message = "La URL publica es obligatoria")
        @Size(max = 80, message = "La URL publica no puede pasar de 80 caracteres")
        @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$",
                message = "Use solo minusculas, numeros y guiones (ejemplo: barberia-el-corte)")
        String slug,

        @NotBlank(message = "El nombre de la sede es obligatorio")
        @Size(max = 120, message = "El nombre de la sede no puede pasar de 120 caracteres")
        String locationName,

        @Size(max = 200, message = "La direccion no puede pasar de 200 caracteres")
        String address,

        @Size(max = 80, message = "La ciudad no puede pasar de 80 caracteres")
        String city,

        @NotBlank(message = "El correo es obligatorio")
        @Email(message = "El correo no tiene un formato valido")
        @Size(max = 180, message = "El correo no puede pasar de 180 caracteres")
        String email,

        @NotBlank(message = "La contraseña es obligatoria")
        @Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres")
        String password) {
}
