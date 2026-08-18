package com.guardao.backend.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * GUA-8 — Documentacion de la API con springdoc.
 *
 * Declara el esquema de seguridad JWT para que Swagger UI muestre el boton
 * "Authorize" y adjunte el header Authorization en las pruebas manuales.
 *
 * La emision y validacion real del token es GUA-11.
 */
@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI guardaoOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Guardao API")
                        .description("Plataforma de reservas para barberias")
                        .version("v1"))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components().addSecuritySchemes(
                        SECURITY_SCHEME_NAME,
                        new SecurityScheme()
                                .name(SECURITY_SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Token emitido por POST /api/auth/login")));
    }
}
