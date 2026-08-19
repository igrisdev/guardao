package com.guardao.backend.business;

/**
 * GUA-20 — Datos necesarios para dar de alta una barberia.
 *
 * Es el contrato del modulo business, independiente de como llegue la
 * peticion: aqui no hay anotaciones de HTTP ni de validacion de formulario,
 * eso es cosa del controlador (ADR-002).
 *
 * @param rawPassword contraseña en claro. La recibe el servicio, que la
 *                    convierte en hash antes de guardarla; nunca sale de
 *                    esta capa ni se registra en el log.
 */
public record RegistrationCommand(
        String businessName,
        String slug,
        String locationName,
        String address,
        String city,
        String email,
        String rawPassword) {
}
