package com.guardao.backend.business;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * GUA-25 — Datos de una sede al crearla o editarla.
 *
 * No incluye el negocio, y es a proposito: sale del token de quien hace la
 * peticion. Aceptarlo aqui permitiria crear sedes dentro de la barberia de
 * otro con solo cambiar un campo del cuerpo (ADR-004).
 *
 * Tampoco incluye si la sede esta activa: eso se cambia con sus propias
 * operaciones, para que una edicion de la direccion no pueda apagar una sede
 * sin querer.
 */
public record LocationRequest(

        @NotBlank(message = "El nombre de la sede es obligatorio")
        @Size(max = 120, message = "El nombre no puede pasar de 120 caracteres")
        String name,

        @Size(max = 200, message = "La direccion no puede pasar de 200 caracteres")
        String address,

        @Size(max = 80, message = "La ciudad no puede pasar de 80 caracteres")
        String city) {
}
