import { ConfigNav } from "@/components/dashboard/config-nav";

/**
 * Panel de configuracion del negocio (Etapa 2).
 *
 * Cuatro pantallas que se navegan entre si: sedes (GUA-36), barberos
 * (GUA-37), servicios y habilidades (GUA-38) y horarios y bloqueos (GUA-39).
 * Todas menos la de sedes trabajan sobre la sede activa que elige el selector
 * de la cabecera.
 */
export default function ConfigLayout({ children }: LayoutProps<"/dashboard/config">) {
  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="font-heading text-2xl font-semibold tracking-tight">Configuracion</h1>
        <p className="text-sm text-muted-foreground">
          Sedes, barberos, servicios y horarios del negocio.
        </p>
      </div>
      <ConfigNav />
      {children}
    </div>
  );
}
