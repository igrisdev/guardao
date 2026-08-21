import * as React from "react"

import { cn } from "@/lib/utils"

/**
 * Casilla sobre el <input type="checkbox"> nativo.
 *
 * accent-color le da el dorado de marca sin reimplementar el control, con lo
 * que se conservan el foco, la barra espaciadora y el anuncio del estado en un
 * lector de pantalla. Reconstruirla con divs es la via rapida a una casilla
 * bonita que no se puede marcar con el teclado.
 */
function Checkbox({ className, ...props }: React.ComponentProps<"input">) {
  return (
    <input
      type="checkbox"
      data-slot="checkbox"
      className={cn(
        "size-4 shrink-0 cursor-pointer rounded-sm border border-input accent-primary outline-none focus-visible:ring-3 focus-visible:ring-ring/50 disabled:cursor-not-allowed disabled:opacity-50",
        className
      )}
      {...props}
    />
  )
}

export { Checkbox }
