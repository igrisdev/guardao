"use client";

import { CalendarClock, CalendarOff, Clock, Pencil, Plus, Trash2 } from "lucide-react";
import * as React from "react";

import { useActiveLocation } from "@/components/dashboard/active-location";
import { Field } from "@/components/dashboard/field";
import { NoLocationSelected } from "@/components/dashboard/no-location";
import { SectionHeader } from "@/components/dashboard/section-header";
import {
  validarSemana,
  WeeklyScheduleEditor,
} from "@/components/dashboard/weekly-schedule-editor";
import { Alert } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Select } from "@/components/ui/select";
import {
  createBlock,
  deleteBlock,
  listBlocks,
  updateBlock,
  type Block,
} from "@/lib/api/blocks";
import { fieldErrors, formErrorMessage } from "@/lib/api/form-errors";
import {
  clearStaffSchedule,
  getLocationSchedule,
  getStaffSchedule,
  replaceLocationSchedule,
  replaceStaffSchedule,
  type ScheduleSlot,
  type ScheduleSlotPayload,
} from "@/lib/api/schedule";
import { listStaff, type Staff } from "@/lib/api/staff";

/**
 * GUA-39 — Horario de la sede, horario propio de cada barbero y bloqueos.
 *
 * Es la pantalla mas delicada de la configuracion: de lo que se escriba aqui
 * sale lo que el cliente ve al reservar. Un horario mal entendido no da error,
 * da citas imposibles.
 *
 * Por eso la pantalla dice en voz alta las tres cosas que se malinterpretan:
 *
 *   1. un dia sin franjas esta cerrado, no "sin configurar"
 *   2. el horario del barbero vale DENTRO del de la sede, no lo reemplaza
 *   3. no tener horario propio no es lo mismo que tener una semana vacia: lo
 *      primero es seguir el de la sede, lo segundo es no trabajar nunca
 */
export default function ConfigSchedulePage() {
  const { activeLocationId, activeLocation } = useActiveLocation();

  if (!activeLocationId) {
    return (
      <>
        <SectionHeader icon={Clock} title="Horarios" />
        <NoLocationSelected what="horarios" />
      </>
    );
  }

  return (
    <SchedulesAndBlocks
      key={activeLocationId}
      locationId={activeLocationId}
      locationName={activeLocation?.name ?? ""}
    />
  );
}

/** Solo el dia de la semana y las horas: el id no se manda al reemplazar. */
function aPayload(slots: ScheduleSlot[]): ScheduleSlotPayload[] {
  return slots.map((slot) => ({
    dayOfWeek: slot.dayOfWeek,
    openTime: slot.openTime.slice(0, 5),
    closeTime: slot.closeTime.slice(0, 5),
  }));
}

function SchedulesAndBlocks({
  locationId,
  locationName,
}: {
  locationId: string;
  locationName: string;
}) {
  const [barberos, setBarberos] = React.useState<Staff[]>([]);
  const [barberoId, setBarberoId] = React.useState<string>("");
  const [cargando, setCargando] = React.useState(true);
  const [error, setError] = React.useState<string | null>(null);

  React.useEffect(() => {
    // `vigente` evita que una respuesta lenta de la sede anterior pise el
    // estado de la nueva cuando alguien cambia de sede dos veces seguidas
    let vigente = true;

    void listStaff(locationId, true)
      .then((lista) => {
        if (!vigente) return;
        setBarberos(lista);
        setBarberoId((actual) => actual || lista[0]?.id || "");
      })
      .catch((err) => {
        if (vigente) setError(formErrorMessage(err) ?? "No se pudieron cargar los barberos");
      })
      .finally(() => {
        if (vigente) setCargando(false);
      });

    return () => {
      vigente = false;
    };
  }, [locationId]);

  const barbero = barberos.find((cada) => cada.id === barberoId) ?? null;

  return (
    <div className="flex flex-col gap-8">
      {error ? <Alert>{error}</Alert> : null}

      <LocationScheduleSection locationId={locationId} locationName={locationName} />

      <div className="flex flex-col gap-5">
        <SectionHeader
          icon={CalendarClock}
          title="Horario por barbero"
          description="Solo para quien no trabaja el horario completo de la sede. Vale siempre dentro del horario general."
        />

        {cargando ? (
          <p className="text-sm text-muted-foreground">Cargando barberos...</p>
        ) : barberos.length === 0 ? (
          <p className="rounded-lg border border-dashed border-border p-8 text-center text-sm text-muted-foreground">
            Esta sede no tiene barberos activos. Agregalos en la pestaña Barberos.
          </p>
        ) : (
          <>
            <div className="max-w-xs">
              <Field id="horario-barbero" label="Barbero">
                <Select
                  id="horario-barbero"
                  value={barberoId}
                  onChange={(event) => setBarberoId(event.target.value)}
                >
                  {barberos.map((cada) => (
                    <option key={cada.id} value={cada.id}>
                      {cada.name}
                    </option>
                  ))}
                </Select>
              </Field>
            </div>

            {barbero ? (
              <>
                {/* La key remonta las dos secciones al cambiar de barbero: sin
                    ella se veria el horario del anterior hasta que respondan
                    las peticiones nuevas */}
                <StaffScheduleSection
                  key={`horario-${barbero.id}`}
                  locationId={locationId}
                  staff={barbero}
                />
                <BlocksSection
                  key={`bloqueos-${barbero.id}`}
                  locationId={locationId}
                  staff={barbero}
                />
              </>
            ) : null}
          </>
        )}
      </div>
    </div>
  );
}

function LocationScheduleSection({
  locationId,
  locationName,
}: {
  locationId: string;
  locationName: string;
}) {
  const [slots, setSlots] = React.useState<ScheduleSlotPayload[]>([]);
  const [cargando, setCargando] = React.useState(true);
  const [guardando, setGuardando] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);
  const [guardado, setGuardado] = React.useState(false);

  React.useEffect(() => {
    let vigente = true;

    getLocationSchedule(locationId)
      .then((horario) => {
        if (vigente) setSlots(aPayload(horario));
      })
      .catch((err) => {
        if (vigente) setError(formErrorMessage(err) ?? "No se pudo cargar el horario de la sede");
      })
      .finally(() => {
        if (vigente) setCargando(false);
      });

    return () => {
      vigente = false;
    };
  }, [locationId]);

  async function guardar() {
    setError(null);
    setGuardado(false);

    const problema = validarSemana(slots);
    if (problema) {
      setError(problema);
      return;
    }

    setGuardando(true);
    try {
      setSlots(aPayload(await replaceLocationSchedule(locationId, slots)));
      setGuardado(true);
    } catch (err) {
      setError(formErrorMessage(err) ?? "No se pudo guardar el horario");
    } finally {
      setGuardando(false);
    }
  }

  return (
    <div className="flex flex-col gap-5">
      <SectionHeader
        icon={Clock}
        title="Horario de la sede"
        description={`Cuando esta abierta ${locationName || "esta sede"}. Un dia sin franjas esta cerrado.`}
        action={
          <Button disabled={cargando || guardando} onClick={() => void guardar()}>
            {guardando ? "Guardando..." : "Guardar horario"}
          </Button>
        }
      />

      {error ? <Alert>{error}</Alert> : null}
      {guardado ? (
        <p className="text-sm text-muted-foreground" role="status">
          Horario guardado.
        </p>
      ) : null}

      {cargando ? (
        <p className="text-sm text-muted-foreground">Cargando horario...</p>
      ) : (
        <WeeklyScheduleEditor
          slots={slots}
          disabled={guardando}
          onChange={(nuevas) => {
            setSlots(nuevas);
            setGuardado(false);
          }}
        />
      )}
    </div>
  );
}

/**
 * Horario propio de un barbero.
 *
 * Empieza en "sigue el horario de la sede", que es el caso de la mayoria, y
 * solo si alguien pulsa se abre el editor. Mostrar de entrada siete dias
 * vacios invitaria a llenarlos, y un barbero con la semana explicitamente
 * vacia no trabaja nunca — justo lo contrario de lo que se queria decir.
 */
function StaffScheduleSection({ locationId, staff }: { locationId: string; staff: Staff }) {
  const [slots, setSlots] = React.useState<ScheduleSlotPayload[]>([]);
  const [tienePropio, setTienePropio] = React.useState(false);
  const [editando, setEditando] = React.useState(false);
  const [cargando, setCargando] = React.useState(true);
  const [guardando, setGuardando] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);

  React.useEffect(() => {
    let vigente = true;

    getStaffSchedule(locationId, staff.id)
      .then((horario) => {
        if (!vigente) return;
        setSlots(aPayload(horario));
        setTienePropio(horario.length > 0);
        setEditando(horario.length > 0);
      })
      .catch((err) => {
        if (vigente) setError(formErrorMessage(err) ?? "No se pudo cargar el horario del barbero");
      })
      .finally(() => {
        if (vigente) setCargando(false);
      });

    return () => {
      vigente = false;
    };
  }, [locationId, staff.id]);

  async function guardar() {
    setError(null);

    const problema = validarSemana(slots);
    if (problema) {
      setError(problema);
      return;
    }

    setGuardando(true);
    try {
      const horario = await replaceStaffSchedule(locationId, staff.id, slots);
      setSlots(aPayload(horario));
      setTienePropio(horario.length > 0);
    } catch (err) {
      setError(formErrorMessage(err) ?? "No se pudo guardar el horario");
    } finally {
      setGuardando(false);
    }
  }

  async function volverAlDeLaSede() {
    setError(null);
    setGuardando(true);
    try {
      await clearStaffSchedule(locationId, staff.id);
      setSlots([]);
      setTienePropio(false);
      setEditando(false);
    } catch (err) {
      setError(formErrorMessage(err) ?? "No se pudo quitar el horario propio");
    } finally {
      setGuardando(false);
    }
  }

  if (cargando) {
    return <p className="text-sm text-muted-foreground">Cargando horario de {staff.name}...</p>;
  }

  return (
    <div className="flex flex-col gap-3 rounded-xl border border-border bg-card p-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-2">
          <span className="text-sm font-medium">{staff.name}</span>
          <Badge variant={tienePropio ? "active" : "default"}>
            {tienePropio ? "Horario propio" : "Sigue el horario de la sede"}
          </Badge>
        </div>

        <div className="flex flex-wrap gap-2">
          {editando ? (
            <Button size="sm" disabled={guardando} onClick={() => void guardar()}>
              {guardando ? "Guardando..." : "Guardar"}
            </Button>
          ) : (
            <Button size="sm" variant="outline" onClick={() => setEditando(true)}>
              Definir horario propio
            </Button>
          )}
          {tienePropio ? (
            <Button
              size="sm"
              variant="ghost"
              disabled={guardando}
              onClick={() => void volverAlDeLaSede()}
            >
              Volver al de la sede
            </Button>
          ) : null}
        </div>
      </div>

      {error ? <Alert>{error}</Alert> : null}

      {editando ? (
        <>
          <p className="text-xs text-muted-foreground">
            Vale dentro del horario de la sede: si aqui pones de 7 a 19 y la sede abre de 8 a 18,
            {" "}
            {staff.name} atiende de 8 a 18.
          </p>
          <WeeklyScheduleEditor slots={slots} disabled={guardando} onChange={setSlots} />
        </>
      ) : null}
    </div>
  );
}

/** Convierte un instante ISO al valor que espera un <input type="datetime-local">. */
function aInputLocal(iso: string): string {
  const fecha = new Date(iso);
  const dosDigitos = (n: number) => String(n).padStart(2, "0");

  return (
    `${fecha.getFullYear()}-${dosDigitos(fecha.getMonth() + 1)}-${dosDigitos(fecha.getDate())}` +
    `T${dosDigitos(fecha.getHours())}:${dosDigitos(fecha.getMinutes())}`
  );
}

const FORMATO_FECHA = new Intl.DateTimeFormat("es-CO", {
  dateStyle: "medium",
  timeStyle: "short",
});

/**
 * Vacaciones, dias libres y ausencias puntuales de un barbero.
 *
 * Estos si se borran de verdad, a diferencia de barberos y servicios: unas
 * vacaciones canceladas no son historial, y ese rato tiene que volver a estar
 * libre.
 */
function BlocksSection({ locationId, staff }: { locationId: string; staff: Staff }) {
  const [bloqueos, setBloqueos] = React.useState<Block[]>([]);
  const [cargando, setCargando] = React.useState(true);
  const [error, setError] = React.useState<string | null>(null);
  const [enCurso, setEnCurso] = React.useState<string | null>(null);
  const [editando, setEditando] = React.useState<Block | null>(null);
  const [creando, setCreando] = React.useState(false);

  const cargar = React.useCallback(
    () =>
      listBlocks(locationId, staff.id)
        .then((lista) => {
          setBloqueos(lista);
          setError(null);
        })
        .catch((err) => setError(formErrorMessage(err) ?? "No se pudieron cargar los bloqueos"))
        .finally(() => setCargando(false)),
    [locationId, staff.id],
  );

  React.useEffect(() => {
    void cargar();
  }, [cargar]);

  async function borrar(bloqueo: Block) {
    setError(null);
    setEnCurso(bloqueo.id);
    try {
      await deleteBlock(locationId, staff.id, bloqueo.id);
      await cargar();
    } catch (err) {
      setError(formErrorMessage(err) ?? "No se pudo quitar el bloqueo");
    } finally {
      setEnCurso(null);
    }
  }

  return (
    <div className="flex flex-col gap-4">
      <SectionHeader
        icon={CalendarOff}
        title="Dias libres y vacaciones"
        description={`Ratos en los que ${staff.name} no atiende, aunque la sede este abierta.`}
        action={
          <Button size="sm" onClick={() => setCreando(true)}>
            <Plus data-icon="inline-start" />
            Nuevo bloqueo
          </Button>
        }
      />

      {error ? <Alert>{error}</Alert> : null}
      {cargando ? <p className="text-sm text-muted-foreground">Cargando bloqueos...</p> : null}

      {!cargando && bloqueos.length === 0 ? (
        <p className="rounded-lg border border-dashed border-border p-6 text-center text-sm text-muted-foreground">
          Sin bloqueos. {staff.name} atiende segun su horario.
        </p>
      ) : (
        <ul className="flex flex-col gap-2">
          {bloqueos.map((bloqueo) => (
            <li
              key={bloqueo.id}
              className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-border bg-card p-3"
            >
              <div className="min-w-0">
                <p className="text-sm">
                  {FORMATO_FECHA.format(new Date(bloqueo.startAt))} —{" "}
                  {FORMATO_FECHA.format(new Date(bloqueo.endAt))}
                </p>
                {bloqueo.reason ? (
                  <p className="text-xs text-muted-foreground">{bloqueo.reason}</p>
                ) : null}
              </div>

              <div className="flex shrink-0 gap-2">
                <Button variant="ghost" size="sm" onClick={() => setEditando(bloqueo)}>
                  <Pencil data-icon="inline-start" />
                  Editar
                </Button>
                <Button
                  variant="destructive"
                  size="sm"
                  disabled={enCurso === bloqueo.id}
                  onClick={() => void borrar(bloqueo)}
                >
                  <Trash2 data-icon="inline-start" />
                  Quitar
                </Button>
              </div>
            </li>
          ))}
        </ul>
      )}

      {/* Montada solo cuando hace falta y con key, para que sus campos se
          reinicien al pasar de un bloqueo a otro sin un efecto que los
          reescriba */}
      {creando || editando ? (
        <BlockDialog
          key={editando?.id ?? "nuevo"}
          locationId={locationId}
          staff={staff}
          block={editando}
          onClose={() => {
            setCreando(false);
            setEditando(null);
          }}
          onSaved={cargar}
        />
      ) : null}
    </div>
  );
}

function BlockDialog({
  locationId,
  staff,
  block,
  onClose,
  onSaved,
}: {
  locationId: string;
  staff: Staff;
  block: Block | null;
  onClose: () => void;
  onSaved: () => Promise<void>;
}) {
  const [startAt, setStartAt] = React.useState(block ? aInputLocal(block.startAt) : "");
  const [endAt, setEndAt] = React.useState(block ? aInputLocal(block.endAt) : "");
  const [reason, setReason] = React.useState(block?.reason ?? "");
  const [errores, setErrores] = React.useState<Record<string, string>>({});
  const [errorGeneral, setErrorGeneral] = React.useState<string | null>(null);
  const [guardando, setGuardando] = React.useState(false);

  async function guardar(event: React.FormEvent) {
    event.preventDefault();
    setErrores({});
    setErrorGeneral(null);

    if (startAt >= endAt) {
      setErrorGeneral("El fin del bloqueo debe ser posterior a su inicio.");
      return;
    }

    setGuardando(true);
    const payload = {
      // datetime-local entrega hora local del navegador; toISOString la
      // convierte al instante con zona que espera el backend
      startAt: new Date(startAt).toISOString(),
      endAt: new Date(endAt).toISOString(),
      reason: reason.trim() || null,
    };

    try {
      if (block) {
        await updateBlock(locationId, staff.id, block.id, payload);
      } else {
        await createBlock(locationId, staff.id, payload);
      }
      await onSaved();
      onClose();
    } catch (err) {
      setErrores(fieldErrors(err));
      setErrorGeneral(formErrorMessage(err));
    } finally {
      setGuardando(false);
    }
  }

  return (
    <Dialog
      open
      onClose={onClose}
      title={block ? "Editar bloqueo" : "Nuevo bloqueo"}
      description={`${staff.name} dejara de aparecer disponible en ese rango.`}
    >
      <form onSubmit={guardar} className="flex flex-col gap-4">
        {errorGeneral ? <Alert>{errorGeneral}</Alert> : null}

        <Field id="bloqueo-inicio" label="Desde" error={errores.startAt}>
          <Input
            id="bloqueo-inicio"
            type="datetime-local"
            value={startAt}
            onChange={(event) => setStartAt(event.target.value)}
            required
            aria-invalid={Boolean(errores.startAt)}
          />
        </Field>

        <Field id="bloqueo-fin" label="Hasta" error={errores.endAt}>
          <Input
            id="bloqueo-fin"
            type="datetime-local"
            value={endAt}
            onChange={(event) => setEndAt(event.target.value)}
            required
            aria-invalid={Boolean(errores.endAt)}
          />
        </Field>

        <Field
          id="bloqueo-motivo"
          label="Motivo"
          error={errores.reason}
          hint="Opcional. Solo lo ves tu; el cliente nunca lo lee."
        >
          <Input
            id="bloqueo-motivo"
            value={reason}
            onChange={(event) => setReason(event.target.value)}
            placeholder="Vacaciones"
            maxLength={200}
            aria-invalid={Boolean(errores.reason)}
          />
        </Field>

        <div className="mt-1 flex justify-end gap-2">
          <Button type="button" variant="outline" onClick={onClose}>
            Cancelar
          </Button>
          <Button type="submit" disabled={guardando}>
            {guardando ? "Guardando..." : "Guardar"}
          </Button>
        </div>
      </form>
    </Dialog>
  );
}
