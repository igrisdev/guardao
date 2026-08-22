import Link from "next/link";

import { RequireGuest } from "@/components/auth/require-guest";
import { BrandMark } from "@/components/brand-mark";

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
            <Link href="/">
              <BrandMark />
            </Link>
            <Link href="/register" className="text-sm text-muted-foreground hover:text-foreground">
              ¿No tienes cuenta? <span className="font-medium text-primary">Regístrate</span>
            </Link>
          </div>
        </header>
        <main className="mx-auto w-full max-w-2xl flex-1 px-4 py-8">{children}</main>
      </div>
    </RequireGuest>
  );
}
