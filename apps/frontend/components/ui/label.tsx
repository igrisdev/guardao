import * as React from "react"

import { cn } from "@/lib/utils"

/**
 * Etiqueta de un campo. Se usa siempre con htmlFor apuntando al id del
 * control: un placeholder no es una etiqueta —desaparece al escribir y los
 * lectores de pantalla no siempre lo anuncian—, asi que todo campo del panel
 * de configuracion lleva la suya.
 */
function Label({ className, ...props }: React.ComponentProps<"label">) {
  return (
    <label
      data-slot="label"
      className={cn(
        "flex items-center gap-2 text-sm leading-none font-medium text-foreground select-none",
        className
      )}
      {...props}
    />
  )
}

export { Label }
