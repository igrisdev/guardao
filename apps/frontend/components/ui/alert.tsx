import { AlertTriangle } from "lucide-react"
import * as React from "react"

import { cn } from "@/lib/utils"

/**
 * Aviso de error sobre un formulario o una lista.
 *
 * role="alert" no es decorativo: es lo que hace que un lector de pantalla
 * anuncie el mensaje en cuanto aparece. Sin el, alguien que no ve la pantalla
 * pulsa "Guardar", no pasa nada aparente y no tiene forma de enterarse de por
 * que.
 */
function Alert({ className, children, ...props }: React.ComponentProps<"div">) {
  return (
    <div
      role="alert"
      data-slot="alert"
      className={cn(
        "flex items-start gap-2 rounded-lg border border-destructive/40 bg-destructive/10 px-3 py-2 text-sm text-destructive",
        className
      )}
      {...props}
    >
      <AlertTriangle aria-hidden className="mt-0.5 size-4 shrink-0" />
      <div>{children}</div>
    </div>
  )
}

export { Alert }
