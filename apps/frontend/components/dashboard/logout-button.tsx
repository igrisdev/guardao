"use client";

import { useRouter } from "next/navigation";
import { LogOut } from "lucide-react";

import { logout } from "@/lib/api";
import { SidebarMenuButton } from "@/components/ui/sidebar";

/**
 * GUA-28 — Cierra la sesion y vuelve a /login.
 *
 * router.replace y no push: la entrada de /dashboard en el historial se
 * reemplaza por /login en vez de apilarse, asi que "atras" desde /login no
 * cae directo en la pantalla que se acaba de cerrar. Las demas paginas del
 * dashboard que el usuario haya visitado (Agenda, Config) siguen en el
 * historial mas atras, pero RequireSession vuelve a montarse al navegar a
 * ellas (es la raiz del layout de /dashboard) y relee la sesion ya vacia,
 * asi que rebotan a /login igual.
 */
export function LogoutButton() {
  const router = useRouter();

  function handleLogout() {
    logout();
    router.replace("/login");
  }

  return (
    <SidebarMenuButton onClick={handleLogout}>
      <LogOut />
      <span>Cerrar sesión</span>
    </SidebarMenuButton>
  );
}
