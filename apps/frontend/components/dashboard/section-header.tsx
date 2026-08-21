import type { LucideIcon } from "lucide-react"

/**
 * Encabezado de seccion del sistema de diseño (plan del proyecto, seccion 6.5):
 * icono en cuadrado redondeado con fondo `accent`, y al lado el titulo con su
 * descripcion.
 *
 * Existe como componente y no copiado en cada pagina para que las cuatro
 * pantallas de configuracion se vean iguales: en cuanto se repite a mano, una
 * termina con el icono de otro tamaño y se nota.
 */
export function SectionHeader({
  icon: Icon,
  title,
  description,
  action,
}: {
  icon: LucideIcon
  title: string
  description?: string
  action?: React.ReactNode
}) {
  return (
    <div className="flex items-start justify-between gap-4">
      <div className="flex items-start gap-3">
        <span className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-accent text-accent-foreground">
          <Icon aria-hidden className="size-4.5" />
        </span>
        <div>
          <h2 className="font-heading text-lg leading-snug font-semibold">{title}</h2>
          {description ? (
            <p className="mt-0.5 text-[13px] text-muted-foreground">{description}</p>
          ) : null}
        </div>
      </div>
      {action ? <div className="shrink-0">{action}</div> : null}
    </div>
  )
}
