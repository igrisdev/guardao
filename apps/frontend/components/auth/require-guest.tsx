"use client";

import { useEffect, useSyncExternalStore } from "react";
import { useRouter } from "next/navigation";

import { isAuthenticated } from "@/lib/api/auth";

// Mismo motivo que en RequireSession: no hay evento de "cambio" de
// localStorage dentro de la misma pestaña que valga la pena escuchar, y
// login/logout ya provocan una navegacion que vuelve a montar este
// componente.
function subscribe() {
  return () => {};
}

function getSnapshot() {
  return isAuthenticated();
}

// En el servidor no hay localStorage, asi que el snapshot del servidor
// siempre es "no autenticado". Eso deja pasar el render inicial del
// formulario de registro para el caso comun (un visitante sin sesion) sin
// parpadeo, al costo de un parpadeo breve para quien ya tiene sesion y
// entra directo a /register (se corrige en el primer render del cliente).
function getServerSnapshot() {
  return false;
}

/**
 * Guard de las rutas publicas de autenticacion (registro, y a futuro login).
 *
 * Espejo de RequireSession (components/dashboard/require-session.tsx): en
 * vez de exigir sesion, la rechaza. Quien ya tiene una cuenta iniciada no
 * deberia poder volver a pasar por el registro, asi que se le manda directo
 * al dashboard.
 */
export function RequireGuest({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const authenticated = useSyncExternalStore(subscribe, getSnapshot, getServerSnapshot);

  useEffect(() => {
    if (authenticated) {
      router.replace("/dashboard");
    }
  }, [authenticated, router]);

  // Con sesion confirmada no se renderiza el formulario: evita el parpadeo
  // del registro antes del redirect.
  if (authenticated) return null;

  return <>{children}</>;
}
