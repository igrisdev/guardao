import * as React from "react"

import { Label } from "@/components/ui/label"

/**
 * Etiqueta, control y mensaje de error de un campo de formulario.
 *
 * El mensaje de error se enlaza con aria-describedby en vez de quedarse suelto
 * debajo: asi un lector de pantalla lo lee al llegar al campo y no hay que ir a
 * buscarlo. Por eso el id es obligatorio y el control de dentro tiene que
 * usarlo — es lo que ata las tres piezas.
 */
export function Field({
  id,
  label,
  error,
  hint,
  children,
}: {
  id: string
  label: string
  error?: string
  hint?: string
  children: React.ReactNode
}) {
  return (
    <div className="flex flex-col gap-1.5">
      <Label htmlFor={id}>{label}</Label>
      {children}
      {hint && !error ? (
        <p id={`${id}-hint`} className="text-xs text-muted-foreground">
          {hint}
        </p>
      ) : null}
      {error ? (
        <p id={`${id}-error`} className="text-xs text-destructive">
          {error}
        </p>
      ) : null}
    </div>
  )
}
