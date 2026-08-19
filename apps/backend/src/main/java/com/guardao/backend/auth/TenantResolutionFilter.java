package com.guardao.backend.auth;

import com.guardao.backend.shared.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * GUA-22 — Resuelve el negocio de la peticion y lo deja en el contexto.
 *
 * Es la primera de las tres capas del aislamiento (ADR-004): el business_id
 * sale del JWT ya validado, nunca de un parametro que el cliente controle.
 *
 * Va despues del filtro de token portador en la cadena de seguridad, porque
 * necesita la autenticacion ya resuelta. En las rutas publicas simplemente no
 * hay autenticacion y el contexto queda vacio, que es lo correcto: el
 * registro y el login trabajan sin negocio, y la pagina publica de reservas
 * lo resuelve por el slug.
 *
 * La limpieza va en un finally y no es un detalle de estilo: el servidor
 * reutiliza los hilos entre peticiones, asi que un contexto sin limpiar le
 * entregaria a la siguiente peticion el negocio de la anterior. Seria una
 * fuga de datos entre clientes, que es exactamente lo que esto evita.
 */
public class TenantResolutionFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        try {
            resolverNegocio();
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private void resolverNegocio() {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();

        if (autenticacion instanceof JwtAuthenticationToken jwtAuth && autenticacion.isAuthenticated()) {
            AuthenticatedUser usuario = CurrentUser.fromJwt(jwtAuth.getToken());

            // Los SUPER_ADMIN no tienen negocio (GUA-24), asi que el contexto
            // queda vacio y sus consultas no se filtran. Es deliberado: el
            // panel interno necesita ver todas las barberias. Por eso mismo,
            // los endpoints de una barberia exigen OWNER o STAFF y no aceptan
            // a un SUPER_ADMIN, que ahi no tendria negocio con que trabajar.
            if (usuario.businessId() != null) {
                TenantContext.set(usuario.businessId());
            }
        }
    }
}
