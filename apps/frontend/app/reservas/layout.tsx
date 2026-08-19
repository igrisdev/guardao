import Link from "next/link";

/**
 * Layout de la pagina publica de reservas: completamente independiente de
 * DashboardLayout. Sin Sidebar, sin nada que dependa de una sesion
 * iniciada — un cliente sin cuenta debe poder reservar (ver
 * docs/adr/006-cliente-sin-cuenta.md). Su propia navegacion es apenas un
 * encabezado con un enlace a "gestionar mi cita".
 */
export default function BookingLayout({ children }: LayoutProps<"/reservas">) {
  return (
    <div className="flex min-h-svh flex-col bg-background">
      <header className="border-b border-border">
        <div className="mx-auto flex w-full max-w-3xl items-center justify-between px-4 py-4">
          <Link href="/reservas" className="text-sm font-semibold tracking-tight text-foreground">
            Reservar cita
          </Link>
          <Link
            href="/reservas/gestionar"
            className="text-sm text-muted-foreground hover:text-foreground"
          >
            Gestionar mi cita
          </Link>
        </div>
      </header>
      <main className="mx-auto w-full max-w-3xl flex-1 px-4 py-8">{children}</main>
    </div>
  );
}
