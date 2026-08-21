package com.guardao.backend.staff;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * GUA-31 — Datos de un barbero al crearlo o editarlo.
 *
 * No incluye la sede: va en la URL, y de ahi se verifica que sea del negocio
 * del token. Aceptarla en el cuerpo permitiria crear barberos dentro de la
 * barberia de otro con solo cambiar un campo (ADR-004).
 *
 * Tampoco incluye si esta activo: eso se cambia con sus propias operaciones,
 * para que corregir un nombre mal escrito no pueda apagar a un barbero sin
 * querer.
 */
public record StaffRequest(

        @NotBlank(message = "El nombre del barbero es obligatorio")
        @Size(max = 120, message = "El nombre no puede pasar de 120 caracteres")
        String name) {
}
