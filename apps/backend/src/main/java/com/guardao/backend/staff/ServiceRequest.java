package com.guardao.backend.staff;

import com.guardao.backend.shared.validation.HalfHourStep;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * GUA-31 — Datos de un servicio al crearlo o editarlo.
 *
 * price y durationMin son Integer y no int a proposito: con int, un cuerpo
 * que omita el campo llegaria como 0 y pasaria por un precio o una duracion
 * escritos de verdad. Con el objeto llega nulo y @NotNull lo rechaza diciendo
 * que falta.
 *
 * El precio va en pesos enteros, sin decimales (Tech Spec 3.1). Se permite el
 * cero: hay barberias que regalan el arreglo de barba con el corte y lo
 * quieren igual en la lista para poder agendarlo.
 */
public record ServiceRequest(

        @NotBlank(message = "El nombre del servicio es obligatorio")
        @Size(max = 120, message = "El nombre no puede pasar de 120 caracteres")
        String name,

        @NotNull(message = "El precio es obligatorio")
        @PositiveOrZero(message = "El precio no puede ser negativo")
        Integer price,

        @NotNull(message = "La duracion es obligatoria")
        @HalfHourStep
        Integer durationMin) {
}
