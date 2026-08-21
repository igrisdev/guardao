"use client";

import { Plus, Trash2 } from "lucide-react";
import * as React from "react";

import { Button } from "@/components/ui/button";
import { Select } from "@/components/ui/select";
import type { ScheduleSlotPayload } from "@/lib/api/schedule";
import { DAY_NAMES, HALF_HOURS } from "@/lib/format";

/**
 * GUA-39 — Editor del horario de una semana.
 *
 * El mismo componente sirve para el horario de la sede y para el de un
 * barbero, porque son la misma estructura: siete dias, cada uno con cero o mas
 * franjas. Lo que cambia es a donde se guarda, y de eso se encarga la pagina.
 *
 * Dos decisiones de forma que vienen del dominio, no del gusto:
 *
 * - Cada dia lista sus franjas en vertical y tiene su propio boton de agregar.
 *   Un solo "agregar franja" general obligaria a elegir el dia en un
 *   desplegable mas, y la jornada partida —el caso que este ticket existe para
 *   resolver— dejaria de verse de un golpe.
 * - Las horas son desplegables de media en media. El backend rechaza cualquier
 *   otra cosa, asi que un campo libre solo serviria para escribir un 8:15 que
 *   va a fallar. Ofrecer lo valido explica la regla sin tener que enunciarla.
 *
 * Un dia sin franjas es un dia cerrado, y se dice con todas sus letras en
 * pantalla: dejarlo vacio y en silencio se lee como "falta llenarlo".
 */
export function WeeklyScheduleEditor({
  slots,
  onChange,
  disabled = false,
}: {
  slots: ScheduleSlotPayload[];
  onChange: (slots: ScheduleSlotPayload[]) => void;
  disabled?: boolean;
}) {
  function agregar(dayOfWeek: number) {
    // Una franja nueva arranca en un horario de barberia comun para que el
    // caso normal sea pulsar y guardar, no pulsar y corregir dos desplegables
    onChange([...slots, { dayOfWeek, openTime: "09:00", closeTime: "13:00" }]);
  }

  function cambiar(indice: number, cambios: Partial<ScheduleSlotPayload>) {
    onChange(slots.map((slot, i) => (i === indice ? { ...slot, ...cambios } : slot)));
  }

  function quitar(indice: number) {
    onChange(slots.filter((_, i) => i !== indice));
  }

  return (
    <ul className="flex flex-col gap-2">
      {DAY_NAMES.map((nombre, dayOfWeek) => {
        // Se conserva el indice dentro del array completo porque es lo que
        // identifica la franja al cambiarla o quitarla: las franjas no tienen
        // id propio mientras se editan, y el orden dentro del dia cambia en
        // cuanto alguien corrige una hora
        const delDia = slots
          .map((slot, indice) => ({ slot, indice }))
          .filter(({ slot }) => slot.dayOfWeek === dayOfWeek)
          .sort((a, b) => a.slot.openTime.localeCompare(b.slot.openTime));

        return (
          <li key={dayOfWeek} className="rounded-lg border border-border bg-card p-3">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <span className="text-sm font-medium">{nombre}</span>
              <Button
                type="button"
                variant="ghost"
                size="xs"
                disabled={disabled}
                onClick={() => agregar(dayOfWeek)}
              >
                <Plus data-icon="inline-start" />
                Agregar franja
              </Button>
            </div>

            {delDia.length === 0 ? (
              <p className="mt-1 text-xs text-muted-foreground">Cerrado</p>
            ) : (
              <ul className="mt-2 flex flex-col gap-2">
                {delDia.map(({ slot, indice }) => (
                  <li key={indice} className="flex flex-wrap items-center gap-2">
                    <label className="sr-only" htmlFor={`abre-${indice}`}>
                      {nombre}: hora de apertura
                    </label>
                    <Select
                      id={`abre-${indice}`}
                      className="w-24"
                      value={slot.openTime}
                      disabled={disabled}
                      onChange={(event) => cambiar(indice, { openTime: event.target.value })}
                    >
                      {HALF_HOURS.map((hora) => (
                        <option key={hora} value={hora}>
                          {hora}
                        </option>
                      ))}
                    </Select>

                    <span className="text-xs text-muted-foreground">a</span>

                    <label className="sr-only" htmlFor={`cierra-${indice}`}>
                      {nombre}: hora de cierre
                    </label>
                    <Select
                      id={`cierra-${indice}`}
                      className="w-24"
                      value={slot.closeTime}
                      disabled={disabled}
                      onChange={(event) => cambiar(indice, { closeTime: event.target.value })}
                    >
                      {HALF_HOURS.map((hora) => (
                        <option key={hora} value={hora}>
                          {hora}
                        </option>
                      ))}
                    </Select>

                    <Button
                      type="button"
                      variant="ghost"
                      size="icon-sm"
                      disabled={disabled}
                      aria-label={`Quitar la franja de ${slot.openTime} a ${slot.closeTime} del ${nombre.toLowerCase()}`}
                      onClick={() => quitar(indice)}
                    >
                      <Trash2 />
                    </Button>
                  </li>
                ))}
              </ul>
            )}
          </li>
        );
      })}
    </ul>
  );
}

/**
 * Las dos reglas que el backend tambien comprueba, adelantadas al formulario.
 *
 * No sustituyen a las del servidor —esas son las que mandan— pero evitan un
 * viaje para decir algo que ya se sabe, y sobre todo permiten señalar el dia
 * concreto: el 409 del servidor llega cuando ya se perdio de vista cual de las
 * catorce franjas de la pantalla era.
 *
 * Devuelve null cuando todo esta bien.
 */
export function validarSemana(slots: ScheduleSlotPayload[]): string | null {
  for (const slot of slots) {
    if (slot.openTime >= slot.closeTime) {
      return `El ${DAY_NAMES[slot.dayOfWeek].toLowerCase()} tiene una franja que cierra antes de abrir.`;
    }
  }

  for (let dia = 0; dia < 7; dia++) {
    const delDia = slots
      .filter((slot) => slot.dayOfWeek === dia)
      .sort((a, b) => a.openTime.localeCompare(b.openTime));

    for (let i = 1; i < delDia.length; i++) {
      // Tocarse no es cruzarse: de 8 a 12 y de 12 a 18 es una jornada seguida
      // partida en dos filas, y el backend tambien la acepta
      if (delDia[i].openTime < delDia[i - 1].closeTime) {
        return `El ${DAY_NAMES[dia].toLowerCase()} tiene dos franjas que se cruzan.`;
      }
    }
  }

  return null;
}
