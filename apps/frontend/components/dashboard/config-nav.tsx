"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

import { cn } from "@/lib/utils";

/**
 * Navegacion entre las cuatro pantallas de configuracion.
 *
 * Son rutas y no pestañas en memoria a proposito: asi cada una tiene su
 * direccion, el boton de atras del navegador funciona, y se puede mandar por
 * chat "revisa los horarios" con un enlace. Pestañas en memoria darian la
 * misma apariencia y ninguna de las tres cosas.
 *
 * La comparacion de la ruta activa es exacta y no por prefijo: con startsWith,
 * "/dashboard/config" quedaria marcada tambien mientras se mira Barberos,
 * porque es prefijo de todas.
 */
const items = [
  { href: "/dashboard/config", label: "Sedes" },
  { href: "/dashboard/config/barberos", label: "Barberos" },
  { href: "/dashboard/config/servicios", label: "Servicios" },
  { href: "/dashboard/config/horarios", label: "Horarios" },
];

export function ConfigNav() {
  const pathname = usePathname();

  return (
    <nav aria-label="Secciones de configuracion" className="border-b border-border">
      <ul className="-mb-px flex gap-1 overflow-x-auto">
        {items.map((item) => {
          const activo = pathname === item.href;

          return (
            <li key={item.href}>
              <Link
                href={item.href}
                aria-current={activo ? "page" : undefined}
                className={cn(
                  "inline-block border-b-2 px-3 py-2 text-sm font-medium whitespace-nowrap transition-colors outline-none focus-visible:ring-3 focus-visible:ring-ring/50",
                  activo
                    ? "border-primary text-foreground"
                    : "border-transparent text-muted-foreground hover:text-foreground",
                )}
              >
                {item.label}
              </Link>
            </li>
          );
        })}
      </ul>
    </nav>
  );
}
