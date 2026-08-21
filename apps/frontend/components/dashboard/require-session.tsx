"use client";

import { useEffect, useSyncExternalStore } from "react";
import { useRouter } from "next/navigation";

import { isAuthenticated } from "@/lib/api/auth";

// No hay un evento de "cambio" de localStorage dentro de la misma pestaña
// que valga la pena escuchar aqui: la sesion solo cambia por login/logout,
// y ambos ya provocan una navegacion que vuelve a montar este componente.
function subscribe() {
  return () => {};
}

function getSnapshot() {
  return isAuthenticated();
}

// En el servidor no existe localStorage: no hay sesion que leer, asi que
// el snapshot del servidor siempre es "no autenticado". Evita el
// parpadeo de contenido protegido antes de que el cliente confirme la
// sesion real.
function getServerSnapshot() {
  return false;
}

/**
 * Guard de las rutas del dashboard.
 *
 * No es un middleware de Next.js a proposito: el accessToken vive en
 * localStorage (el backend no usa cookies, ver lib/api/token-storage.ts),
 * y el middleware corre en el edge/servidor, donde localStorage no existe.
 * Por eso la verificacion tiene que pasar por un componente cliente.
 *
 * Solo comprueba que haya sesion guardada; no valida si el accessToken ya
 * caduco. Un token vencido lo resuelve el refresh automatico del cliente
 * HTTP (ver lib/api/http-client.ts) en la primera peticion que haga la
 * pagina, o termina en este mismo redirect si el refresh tambien falla
 * (setSessionExpiredHandler ya apunta a /login por defecto).
 */
export function RequireSession({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const authenticated = useSyncExternalStore(subscribe, getSnapshot, getServerSnapshot);

  useEffect(() => {
    // Se vuelve a preguntar aqui en vez de mirar `authenticated`, y la
    // diferencia no es cosmetica: durante la hidratacion ese valor todavia es
    // el del servidor, que es siempre false (ver getServerSnapshot), y este
    // efecto corre ANTES de que React lo reemplace por el del cliente.
    // Mirandolo, la primera pasada redirige a /login aunque haya sesion
    // valida guardada, y el dashboard se vuelve inalcanzable. Leer
    // localStorage aqui ya devuelve lo que hay de verdad, porque el efecto
    // solo corre en el navegador.
    if (!isAuthenticated()) {
      router.replace("/login");
    }
  }, [authenticated, router]);

  // Sin sesion confirmada no se renderiza nada del dashboard: evita el
  // parpadeo de contenido protegido antes del redirect.
  if (!authenticated) return null;

  return <>{children}</>;
}
