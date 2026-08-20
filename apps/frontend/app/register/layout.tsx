import Link from "next/link";

import { RequireGuest } from "@/components/auth/require-guest";

/**
 * Registro de una barberia nueva: ruta publica, pero no para quien ya tiene
 * sesion iniciada (RequireGuest lo manda directo al dashboard). Chrome
 * minimo, igual que BookingLayout (app/reservas/layout.tsx): esta pantalla
 * no depende del Sidebar del dashboard, que exige sesion.
 */
export default function RegisterLayout({ children }: LayoutProps<"/register">) {
  return (
    <RequireGuest>
      <div className="flex min-h-svh flex-col bg-background">
        <header className="border-b border-border">
          <div className="mx-auto flex w-full max-w-2xl items-center justify-between px-4 py-4">
            <Link href="/" className="text-sm font-semibold tracking-tight text-foreground">
              Guardao
            </Link>
            <Link href="/login" className="text-sm text-muted-foreground hover:text-foreground">
              ¿Ya tienes cuenta? Inicia sesión
            </Link>
          </div>
        </header>
        <main className="mx-auto w-full max-w-2xl flex-1 px-4 py-8">{children}</main>
      </div>
    </RequireGuest>
  );
}
