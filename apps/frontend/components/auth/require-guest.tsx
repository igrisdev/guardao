"use client";

import { useLayoutEffect, useSyncExternalStore } from "react";
import { useRouter } from "next/navigation";

import { isAuthenticated } from "@/lib/api/auth";

/**
 * Fuerza una relectura de isAuthenticated() apenas el componente se monta en
 * el cliente. Ver el comentario largo en RequireSession
 * (components/dashboard/require-session.tsx), su espejo: solo afecta que
 * tan rapido se pinta el formulario, no la decision de redirigir.
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
 * Guard de las rutas publicas de autenticacion (registro y login).
 *
 * Espejo de RequireSession (components/dashboard/require-session.tsx): en
 * vez de exigir sesion, la rechaza. Quien ya tiene una cuenta iniciada no
 * deberia poder volver a pasar por el registro o el login, asi que se le
 * manda directo al dashboard.
 *
 * GUA-28 — igual que en RequireSession, la decision de redirigir usa
 * isAuthenticated() leido directo en un useLayoutEffect (nunca el
 * `authenticated` de useSyncExternalStore, que en la hidratacion de una
 * carga completa de pagina puede arrancar en un valor que todavia no es el
 * real). Ver el comentario largo alla para el porque completo.
 */
export function RequireGuest({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const authenticated = useSyncExternalStore(subscribe, getSnapshot, getServerSnapshot);

  useLayoutEffect(() => {
    if (isAuthenticated()) {
      router.replace("/dashboard");
    }
  }, [router]);

  // Con sesion confirmada no se renderiza el formulario: evita el parpadeo
  // del registro/login antes del redirect.
  if (authenticated) return null;

  return <>{children}</>;
}
