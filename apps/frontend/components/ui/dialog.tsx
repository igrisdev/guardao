"use client";

import { X } from "lucide-react";
import * as React from "react";

import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

/**
 * Ventana modal para los formularios del panel de configuracion.
 *
 * Escrita a mano y no sobre una libreria de menus: lo que hace falta aqui es
 * poco y conocido —fondo que atenua, Escape para cerrar, foco atrapado
 * mientras esta abierta— y ese poco cabe en este archivo. Traer una
 * dependencia nueva para esto obligaria a todo el equipo a aprender su API
 * para cambiar el ancho de un formulario.
 *
 * Lo que si es obligatorio y esta implementado abajo:
 *
 * - role="dialog" con aria-modal y el titulo enlazado por aria-labelledby, que
 *   es lo que hace que un lector de pantalla anuncie de que va la ventana
 * - Escape cierra, como en cualquier otra ventana del sistema
 * - el foco entra al abrir y no se escapa con el tabulador mientras dure; sin
 *   eso, tabular lleva al formulario de atras, que no se ve
 * - al cerrar, el foco vuelve a donde estaba
 * - el fondo no hace scroll detras de la ventana
 */
interface DialogProps {
  open: boolean;
  onClose: () => void;
  title: string;
  description?: string;
  children: React.ReactNode;
  footer?: React.ReactNode;
}

export function Dialog({ open, onClose, title, description, children, footer }: DialogProps) {
  const panelRef = React.useRef<HTMLDivElement>(null);
  const titleId = React.useId();
  const descriptionId = React.useId();

  React.useEffect(() => {
    if (!open) return;

    const previouslyFocused = document.activeElement as HTMLElement | null;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";

    function focusables(): HTMLElement[] {
      if (!panelRef.current) return [];
      return Array.from(
        panelRef.current.querySelectorAll<HTMLElement>(
          'button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), a[href], [tabindex]:not([tabindex="-1"])',
        ),
      );
    }

    // El primer campo, y si no hay ninguno el panel mismo: abrir una ventana y
    // dejar el foco fuera de ella es lo que rompe la navegacion por teclado
    const first = focusables()[0];
    (first ?? panelRef.current)?.focus();

    function onKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") {
        event.preventDefault();
        onClose();
        return;
      }

      if (event.key !== "Tab") return;

      // Ciclo del tabulador dentro de la ventana: al llegar al final vuelve al
      // principio en vez de saltar al formulario de atras
      const elements = focusables();
      if (elements.length === 0) return;

      const firstElement = elements[0];
      const lastElement = elements[elements.length - 1];
      const active = document.activeElement;

      if (event.shiftKey && active === firstElement) {
        event.preventDefault();
        lastElement.focus();
      } else if (!event.shiftKey && active === lastElement) {
        event.preventDefault();
        firstElement.focus();
      }
    }

    document.addEventListener("keydown", onKeyDown);

    return () => {
      document.removeEventListener("keydown", onKeyDown);
      document.body.style.overflow = previousOverflow;
      previouslyFocused?.focus();
    };
  }, [open, onClose]);

  if (!open) return null;

  return (
    <div
      className="fixed inset-0 z-50 flex items-end justify-center overflow-y-auto bg-background/80 p-0 sm:items-center sm:p-6"
      // Cerrar al pulsar fuera. La comprobacion de que el clic fue en el fondo
      // y no en un hijo evita que arrastrar el raton desde dentro cierre
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) onClose();
      }}
    >
      <div
        ref={panelRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        aria-describedby={description ? descriptionId : undefined}
        tabIndex={-1}
        className={cn(
          "relative w-full max-w-lg rounded-t-xl border border-border bg-card p-5 outline-none sm:rounded-xl",
        )}
      >
        <div className="mb-4 pr-8">
          <h2 id={titleId} className="font-heading text-base leading-snug font-medium">
            {title}
          </h2>
          {description ? (
            <p id={descriptionId} className="mt-1 text-sm text-muted-foreground">
              {description}
            </p>
          ) : null}
        </div>

        <Button
          type="button"
          variant="ghost"
          size="icon-sm"
          onClick={onClose}
          aria-label="Cerrar"
          className="absolute top-4 right-4"
        >
          <X />
        </Button>

        {children}

        {footer ? <div className="mt-5 flex justify-end gap-2">{footer}</div> : null}
      </div>
    </div>
  );
}
