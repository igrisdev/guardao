import { DashboardSidebar } from "@/components/dashboard/dashboard-sidebar";
import { RequireSession } from "@/components/dashboard/require-session";
import { Separator } from "@/components/ui/separator";
import { SidebarInset, SidebarProvider, SidebarTrigger } from "@/components/ui/sidebar";

/**
 * Estructura comun a toda pagina interna/autenticada del dashboard:
 * RequireSession protege la ruta (redirige a /login sin sesion valida) y,
 * una vez confirmada, se muestran el Sidebar de navegacion y el contenido.
 */
export default function DashboardLayout({ children }: LayoutProps<"/dashboard">) {
  return (
    <RequireSession>
      <SidebarProvider>
        <DashboardSidebar />
        <SidebarInset>
          <header className="flex h-14 shrink-0 items-center gap-2 border-b border-border px-4">
            <SidebarTrigger />
            <Separator orientation="vertical" className="h-5" />
            <span className="text-sm font-medium text-muted-foreground">Panel de barberia</span>
          </header>
          <main className="flex-1 p-6">{children}</main>
        </SidebarInset>
      </SidebarProvider>
    </RequireSession>
  );
}
