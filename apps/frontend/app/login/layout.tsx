import Link from "next/link";

import { RequireGuest } from "@/components/auth/require-guest";

/**
 * Login: ruta publica compartida entre OWNER y STAFF (GUA-27), pero no para
 * quien ya tiene sesion iniciada (RequireGuest lo manda directo al
 * dashboard). Mismo chrome minimo que RegisterLayout (app/register/layout.tsx).
 */
export default function LoginLayout({ children }: LayoutProps<"/login">) {
  return (
    <RequireGuest>
      <div className="flex min-h-svh flex-col bg-background">
        <header className="border-b border-border">
          <div className="mx-auto flex w-full max-w-2xl items-center justify-between px-4 py-4">
            <Link href="/" className="text-sm font-semibold tracking-tight text-foreground">
              Guardao
            </Link>
            <Link href="/register" className="text-sm text-muted-foreground hover:text-foreground">
              ¿No tienes cuenta? Regístrate
            </Link>
          </div>
        </header>
        <main className="mx-auto w-full max-w-2xl flex-1 px-4 py-8">{children}</main>
      </div>
    </RequireGuest>
  );
}
