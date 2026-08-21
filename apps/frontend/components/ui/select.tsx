import * as React from "react"
import { ChevronDown } from "lucide-react"

import { cn } from "@/lib/utils"

/**
 * Desplegable sobre el <select> nativo del navegador.
 *
 * Nativo y no un menu propio a proposito: en el celular abre la rueda del
 * sistema, que es lo que la gente ya sabe usar, y trae gratis el teclado, el
 * foco y el lector de pantalla. El panel de configuracion se abre casi siempre
 * desde un escritorio, pero el horario y los bloqueos se ajustan desde el
 * telefono en mitad del dia, que es cuando mas se agradece.
 *
 * Se estiliza el contenedor y no las opciones: como se pinta la lista
 * desplegada lo decide el sistema operativo y no vale la pena pelear con eso.
 */
function Select({ className, children, ...props }: React.ComponentProps<"select">) {
  return (
    <div className="relative w-full">
      <select
        data-slot="select"
        className={cn(
          "h-8 w-full appearance-none rounded-lg border border-input bg-transparent py-1 pr-8 pl-2.5 text-base transition-colors outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 disabled:pointer-events-none disabled:cursor-not-allowed disabled:opacity-50 md:text-sm dark:bg-input/30 [&>option]:bg-popover [&>option]:text-popover-foreground",
          className
        )}
        {...props}
      >
        {children}
      </select>
      <ChevronDown
        aria-hidden
        className="pointer-events-none absolute top-1/2 right-2.5 size-3.5 -translate-y-1/2 text-muted-foreground"
      />
    </div>
  )
}

export { Select }
