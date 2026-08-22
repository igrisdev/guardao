import { Scissors } from "lucide-react";

import { cn } from "@/lib/utils";

/**
 * Logo compartido por los headers publicos (login, registro, reservas) y el
 * Sidebar del dashboard. Una sola fuente de verdad para no repetir el marcado
 * del icono en cada layout.
 */
export function BrandMark({
  className,
  subtitle,
}: {
  className?: string;
  subtitle?: string;
}) {
  return (
    <div className={cn("flex items-center gap-2.5", className)}>
      <span className="flex size-7 shrink-0 items-center justify-center rounded-md bg-primary text-primary-foreground">
        <Scissors className="size-3.5" />
      </span>
      <span className="flex flex-col leading-tight">
        <span className="text-sm font-semibold tracking-tight text-foreground">Guardao</span>
        {subtitle ? (
          <span className="text-xs text-muted-foreground">{subtitle}</span>
        ) : null}
      </span>
    </div>
  );
}
