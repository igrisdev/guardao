import { Store } from "lucide-react"

/**
 * Lo que se muestra en las pantallas que necesitan una sede cuando todavia no
 * hay ninguna elegida.
 *
 * Solo ocurre si el negocio se quedo sin sedes, porque el registro crea la
 * primera y el selector elige sola una en cuanto existe. Aun asi hace falta:
 * sin este aviso la pantalla saldria vacia y parecerian barberos borrados.
 */
export function NoLocationSelected({ what }: { what: string }) {
  return (
    <div className="flex flex-col items-center gap-2 rounded-lg border border-dashed border-border p-10 text-center">
      <Store aria-hidden className="size-5 text-muted-foreground" />
      <p className="text-sm text-muted-foreground">
        Elige una sede en la parte de arriba para ver sus {what}.
      </p>
    </div>
  )
}
