import { ActiveLocationProvider, LocationSwitcher } from "@/components/dashboard/active-location";
import { DashboardSidebar } from "@/components/dashboard/dashboard-sidebar";
import { RequireSession } from "@/components/dashboard/require-session";
import { Separator } from "@/components/ui/separator";
import { SidebarInset, SidebarProvider, SidebarTrigger } from "@/components/ui/sidebar";

/**
 * Estructura comun a toda pagina interna/autenticada del dashboard:
 * RequireSession protege la ruta (redirige a /login sin sesion valida) y,
 * una vez confirmada, se muestran el Sidebar de navegacion y el contenido.
 *
 * GUA-36 — La sede activa se resuelve aqui, por encima de todas las paginas,
 * y no dentro de cada una. Barberos, servicios, horarios y agenda cuelgan de
 * una sede: si cada pagina eligiera la suya, cambiar de sede en una no
 * cambiaria las demas y el dueño estaria viendo dos sedes a la vez sin
 * enterarse. Va dentro de RequireSession porque pedir las sedes necesita
 * sesion.
 */
export default function DashboardLayout({ children }: LayoutProps<"/dashboard">) {
  return (
    <RequireSession>
      <ActiveLocationProvider>
        <SidebarProvider>
          <DashboardSidebar />
          <SidebarInset>
            <header className="flex h-14 shrink-0 items-center gap-2 border-b border-border px-4">
              <SidebarTrigger />
              <Separator orientation="vertical" className="h-5" />
              <span className="hidden text-sm font-medium text-muted-foreground sm:inline">
                Panel de barberia
              </span>
              <div className="ml-auto">
                <LocationSwitcher />
              </div>
            </header>
            <main className="flex-1 p-6">{children}</main>
          </SidebarInset>
        </SidebarProvider>
      </ActiveLocationProvider>
    </RequireSession>
  );
}
