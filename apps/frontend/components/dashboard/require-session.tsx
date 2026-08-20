"use client";

import { useLayoutEffect, useSyncExternalStore } from "react";
import { useRouter } from "next/navigation";

import { isAuthenticated } from "@/lib/api/auth";

/**
 * Fuerza una relectura de isAuthenticated() apenas el componente se monta en
 * el cliente, llamando al callback de React una vez dentro del propio
 * subscribe. Sin esto, useSyncExternalStore nunca vuelve a invocar
 * getSnapshot(): queda pegado en el valor de getServerSnapshot (false,
 * porque el servidor no tiene localStorage) hasta que otra cosa fuerce un
 * re-render. Esto solo afecta que tan rapido se pinta el dashboard tras
 * confirmarse la sesion (ver comentario en el componente sobre por que el
 * redirect no depende de esta correccion).
 */
function subscribe(callback: () => void) {
  callback();
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
 * GUA-28 — La decision de REDIRIGIR y la de que RENDERIZAR estan
 * deliberadamente separadas, en dos mecanismos distintos:
 *
 * - Que renderizar usa `authenticated` (useSyncExternalStore): en la
 *   hidratacion de cualquier carga completa de pagina (F5, URL directa) el
 *   primer render del cliente tiene que coincidir con el del servidor
 *   (false, sin localStorage) o React rompe la hidratacion. Por eso el
 *   dashboard puede tardar un instante en aparecer tras confirmarse la
 *   sesion: es el costo de no romper el hydrate.
 *
 * - Si hay que redirigir usa isAuthenticated() leido directo en un
 *   useLayoutEffect, nunca el valor de arriba. useLayoutEffect solo corre
 *   en el cliente (nunca durante SSR), asi que ahi jamas hay un "false"
 *   fantasma que confundir con uno real: siempre es la sesion de verdad.
 *   Redirigir usando en cambio el `authenticated` de useSyncExternalStore
 *   mandaba a /login con sesion valida en cualquier carga completa de una
 *   ruta anidada (F5 en /dashboard/agenda o /dashboard/config), porque ese
 *   efecto se ejecutaba con el "false" de hidratacion antes de que
 *   useSyncExternalStore alcanzara a corregirlo — y desde /login,
 *   RequireGuest rebotaba de vuelta a /dashboard a secas, sin la subruta.
 *   useLayoutEffect corre antes de pintar y antes de los efectos comunes,
 *   asi que la decision de redirigir queda resuelta con el dato correcto
 *   antes de que el usuario vea nada.
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

  useLayoutEffect(() => {
    if (!isAuthenticated()) {
      router.replace("/login");
    }
  }, [router]);

  // Sin sesion confirmada no se renderiza nada del dashboard: evita el
  // parpadeo de contenido protegido antes del redirect.
  if (!authenticated) return null;

  return <>{children}</>;
}
