"use client";

import { Pencil, Plus, Power, Scissors, Sparkles } from "lucide-react";
import * as React from "react";

import { useActiveLocation } from "@/components/dashboard/active-location";
import { Field } from "@/components/dashboard/field";
import { NoLocationSelected } from "@/components/dashboard/no-location";
import { SectionHeader } from "@/components/dashboard/section-header";
import { Alert } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Dialog } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Select } from "@/components/ui/select";
import { fieldErrors, formErrorMessage } from "@/lib/api/form-errors";
import {
  createService,
  deactivateService,
  listServices,
  reactivateService,
  updateService,
  type Service,
} from "@/lib/api/services";
import { assignSkill, listStaffOfService, revokeSkill } from "@/lib/api/skills";
import { listStaff, type Staff } from "@/lib/api/staff";
import { DURATION_OPTIONS, formatDuration, formatPesos } from "@/lib/format";

/**
 * GUA-38 — Servicios de la sede activa y quien sabe hacer cada uno.
 *
 * Las dos cosas van en la misma pantalla porque se piensan juntas: al crear un
 * servicio nuevo lo siguiente que uno quiere es decir quien lo hace, y tenerlo
 * en otra pestaña convierte una decision en dos viajes.
 */
export default function ConfigServicesPage() {
  const { activeLocationId, activeLocation } = useActiveLocation();

  if (!activeLocationId) {
    return (
      <>
        <SectionHeader icon={Scissors} title="Servicios" />
        <NoLocationSelected what="servicios" />
      </>
    );
  }

  return (
    <ServicesAndSkills
      key={activeLocationId}
      locationId={activeLocationId}
      locationName={activeLocation?.name ?? ""}
    />
  );
}

function ServicesAndSkills({
  locationId,
  locationName,
}: {
  locationId: string;
  locationName: string;
}) {
  const [servicios, setServicios] = React.useState<Service[]>([]);
  const [barberos, setBarberos] = React.useState<Staff[]>([]);
  /** Claves "servicioId:barberoId" de las habilidades asignadas. */
  const [habilidades, setHabilidades] = React.useState<Set<string>>(new Set());
  const [cargando, setCargando] = React.useState(true);
  const [error, setError] = React.useState<string | null>(null);
  const [enCurso, setEnCurso] = React.useState<string | null>(null);

  const [editando, setEditando] = React.useState<Service | null>(null);
  const [creando, setCreando] = React.useState(false);

  // Cadena de promesas sin ningun setState antes de la primera llamada, para
  // poder invocarla desde el efecto sin disparar un render en cascada
  // (react-hooks/set-state-in-effect)
  const cargar = React.useCallback(
    () =>
      // Servicios y barberos no dependen entre si: pedirlos a la vez ahorra un
      // viaje completo de ida y vuelta
      Promise.all([listServices(locationId), listStaff(locationId, true)])
        .then(async ([listaServicios, listaBarberos]) => {
          // Las habilidades se piden por servicio porque el backend no expone
          // la matriz entera. Son pocas peticiones —tantas como servicios
          // tenga la sede— y en paralelo, asi que tarda lo que la mas lenta
          const porServicio = await Promise.all(
            listaServicios.map(async (servicio) => ({
              serviceId: servicio.id,
              staff: await listStaffOfService(locationId, servicio.id),
            })),
          );

          const marcadas = new Set<string>();
          for (const { serviceId, staff } of porServicio) {
            for (const barbero of staff) {
              marcadas.add(`${serviceId}:${barbero.id}`);
            }
          }

          setServicios(listaServicios);
          setBarberos(listaBarberos);
          setHabilidades(marcadas);
          setError(null);
        })
        .catch((err) => setError(formErrorMessage(err) ?? "No se pudieron cargar los servicios"))
        .finally(() => setCargando(false)),
    [locationId],
  );

  React.useEffect(() => {
    void cargar();
  }, [cargar]);

  async function alternarServicio(servicio: Service) {
    setError(null);
    setEnCurso(servicio.id);
    try {
      if (servicio.active) {
        await deactivateService(locationId, servicio.id);
      } else {
        await reactivateService(locationId, servicio.id);
      }
      await cargar();
    } catch (err) {
      setError(formErrorMessage(err) ?? "No se pudo cambiar el estado del servicio");
    } finally {
      setEnCurso(null);
    }
  }

  /**
   * Marca o desmarca una habilidad.
   *
   * La casilla cambia antes de que responda el servidor porque marcar una
   * casilla y esperar medio segundo a que se pinte se siente roto, y aqui son
   * varias seguidas. Si la peticion falla se devuelve al estado anterior y se
   * explica: es preferible a bloquear la tabla entera en cada clic.
   */
  async function alternarHabilidad(serviceId: string, staffId: string, marcar: boolean) {
    const clave = `${serviceId}:${staffId}`;
    setError(null);

    setHabilidades((actual) => {
      const siguiente = new Set(actual);
      if (marcar) {
        siguiente.add(clave);
      } else {
        siguiente.delete(clave);
      }
      return siguiente;
    });

    try {
      if (marcar) {
        await assignSkill(locationId, staffId, serviceId);
      } else {
        await revokeSkill(locationId, staffId, serviceId);
      }
    } catch (err) {
      setHabilidades((actual) => {
        const siguiente = new Set(actual);
        if (marcar) {
          siguiente.delete(clave);
        } else {
          siguiente.add(clave);
        }
        return siguiente;
      });
      setError(formErrorMessage(err) ?? "No se pudo guardar la habilidad");
    }
  }

  const activos = servicios.filter((servicio) => servicio.active);

  return (
    <div className="flex flex-col gap-8">
      <div className="flex flex-col gap-5">
        <SectionHeader
          icon={Scissors}
          title="Servicios"
          description={`Lo que se ofrece en ${locationName || "esta sede"}, con su precio y su duracion.`}
          action={
            <Button onClick={() => setCreando(true)}>
              <Plus data-icon="inline-start" />
              Nuevo servicio
            </Button>
          }
        />

        {error ? <Alert>{error}</Alert> : null}
        {cargando ? <p className="text-sm text-muted-foreground">Cargando servicios...</p> : null}

        <ul className="flex flex-col gap-3">
          {servicios.map((servicio) => (
            <li key={servicio.id} className="rounded-xl border border-border bg-card p-4">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div className="min-w-0">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="font-medium">{servicio.name}</span>
                    {servicio.active ? null : <Badge variant="inactive">Retirado</Badge>}
                  </div>
                  <p className="mt-1 text-[13px] text-muted-foreground">
                    <span className="font-semibold text-primary">
                      {formatPesos(servicio.price)}
                    </span>
                    {" · "}
                    {formatDuration(servicio.durationMin)}
                  </p>
                </div>

                <div className="flex shrink-0 flex-wrap gap-2">
                  <Button variant="ghost" size="sm" onClick={() => setEditando(servicio)}>
                    <Pencil data-icon="inline-start" />
                    Editar
                  </Button>
                  <Button
                    variant={servicio.active ? "destructive" : "outline"}
                    size="sm"
                    disabled={enCurso === servicio.id}
                    onClick={() => void alternarServicio(servicio)}
                  >
                    <Power data-icon="inline-start" />
                    {servicio.active ? "Retirar" : "Volver a ofrecer"}
                  </Button>
                </div>
              </div>
            </li>
          ))}
        </ul>

        {!cargando && servicios.length === 0 ? (
          <p className="rounded-lg border border-dashed border-border p-8 text-center text-sm text-muted-foreground">
            Esta sede todavia no ofrece ningun servicio.
          </p>
        ) : null}
      </div>

      <div className="flex flex-col gap-5">
        <SectionHeader
          icon={Sparkles}
          title="Habilidades"
          description="Quien sabe hacer que. Al reservar, solo se le ofrecen al cliente los barberos capacitados para el servicio que eligio."
        />

        <SkillMatrix
          servicios={activos}
          barberos={barberos}
          habilidades={habilidades}
          onToggle={alternarHabilidad}
        />
      </div>

      {/* Montada solo cuando hace falta y con key: asi los campos se
          reinician al cambiar de servicio sin un efecto que los reescriba */}
      {creando || editando ? (
        <ServiceDialog
          key={editando?.id ?? "nuevo"}
          locationId={locationId}
          service={editando}
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

/**
 * La tabla de servicios contra barberos.
 *
 * Es una tabla de verdad y no una rejilla de divs porque los datos son
 * tabulares: un lector de pantalla anuncia "Corte clasico, Andres Mesa,
 * marcado" gracias a los encabezados de fila y columna, cosa que una rejilla
 * de casillas sueltas no puede dar.
 *
 * Solo aparecen los servicios que se ofrecen y los barberos activos. Un
 * servicio retirado no se puede reservar, asi que decir quien lo haria no
 * cambia nada.
 */
function SkillMatrix({
  servicios,
  barberos,
  habilidades,
  onToggle,
}: {
  servicios: Service[];
  barberos: Staff[];
  habilidades: Set<string>;
  onToggle: (serviceId: string, staffId: string, marcar: boolean) => Promise<void>;
}) {
  if (servicios.length === 0 || barberos.length === 0) {
    return (
      <p className="rounded-lg border border-dashed border-border p-8 text-center text-sm text-muted-foreground">
        Para asignar habilidades hacen falta al menos un servicio activo y un barbero activo en
        esta sede.
      </p>
    );
  }

  return (
    <div className="overflow-x-auto rounded-xl border border-border bg-card">
      <table className="w-full border-collapse text-sm">
        <caption className="sr-only">
          Servicios en las filas y barberos en las columnas. Cada casilla indica si ese barbero
          sabe hacer ese servicio.
        </caption>
        <thead>
          <tr className="border-b border-border">
            <th scope="col" className="p-3 text-left font-medium">
              Servicio
            </th>
            {barberos.map((barbero) => (
              <th key={barbero.id} scope="col" className="p-3 text-center font-medium">
                {barbero.name}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {servicios.map((servicio) => (
            <tr key={servicio.id} className="border-b border-border last:border-0">
              <th scope="row" className="p-3 text-left font-normal">
                {servicio.name}
                <span className="ml-2 text-xs text-muted-foreground">
                  {formatDuration(servicio.durationMin)}
                </span>
              </th>
              {barberos.map((barbero) => {
                const marcada = habilidades.has(`${servicio.id}:${barbero.id}`);

                return (
                  <td key={barbero.id} className="p-3 text-center">
                    <Checkbox
                      checked={marcada}
                      onChange={(event) =>
                        void onToggle(servicio.id, barbero.id, event.target.checked)
                      }
                      aria-label={`${barbero.name} sabe hacer ${servicio.name}`}
                    />
                  </td>
                );
              })}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

/**
 * Alta y edicion de un servicio.
 *
 * La duracion es un desplegable y no un campo libre porque el backend solo
 * acepta multiplos de 30: la agenda se dibuja en bloques de media hora y una
 * duracion suelta deja huecos que nadie puede ocupar. Ofrecer solo lo valido
 * evita que alguien escriba 45 y descubra la regla con un error.
 *
 * El precio si es libre, en pesos enteros y sin puntos: se guarda 25000 y la
 * lista lo muestra formateado.
 */
function ServiceDialog({
  locationId,
  service,
  onClose,
  onSaved,
}: {
  locationId: string;
  service: Service | null;
  onClose: () => void;
  onSaved: () => Promise<void>;
}) {
  const [name, setName] = React.useState(service?.name ?? "");
  const [price, setPrice] = React.useState(service ? String(service.price) : "");
  const [durationMin, setDurationMin] = React.useState(service?.durationMin ?? 30);
  const [errores, setErrores] = React.useState<Record<string, string>>({});
  const [errorGeneral, setErrorGeneral] = React.useState<string | null>(null);
  const [guardando, setGuardando] = React.useState(false);

  async function guardar(event: React.FormEvent) {
    event.preventDefault();
    setGuardando(true);
    setErrores({});
    setErrorGeneral(null);

    const payload = {
      name: name.trim(),
      price: Number(price),
      durationMin,
    };

    try {
      if (service) {
        await updateService(locationId, service.id, payload);
      } else {
        await createService(locationId, payload);
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
      title={service ? "Editar servicio" : "Nuevo servicio"}
      description={
        service
          ? "El precio nuevo rige de aqui en adelante; las citas ya agendadas conservan el que se pacto."
          : "Un corte y un tinturado no duran lo mismo, y por eso cada servicio lleva la suya."
      }
    >
      <form onSubmit={guardar} className="flex flex-col gap-4">
        {errorGeneral ? <Alert>{errorGeneral}</Alert> : null}

        <Field id="servicio-nombre" label="Nombre" error={errores.name}>
          <Input
            id="servicio-nombre"
            value={name}
            onChange={(event) => setName(event.target.value)}
            placeholder="Corte clasico"
            maxLength={120}
            required
            aria-invalid={Boolean(errores.name)}
          />
        </Field>

        <Field
          id="servicio-precio"
          label="Precio (pesos)"
          error={errores.price}
          hint="Sin puntos ni decimales: 25000."
        >
          <Input
            id="servicio-precio"
            type="number"
            inputMode="numeric"
            min={0}
            step={1}
            value={price}
            onChange={(event) => setPrice(event.target.value)}
            placeholder="25000"
            required
            aria-invalid={Boolean(errores.price)}
          />
        </Field>

        <Field
          id="servicio-duracion"
          label="Duracion"
          error={errores.durationMin}
          hint="En bloques de media hora, que es como se dibuja la agenda."
        >
          <Select
            id="servicio-duracion"
            value={durationMin}
            onChange={(event) => setDurationMin(Number(event.target.value))}
            aria-invalid={Boolean(errores.durationMin)}
          >
            {DURATION_OPTIONS.map((minutos) => (
              <option key={minutos} value={minutos}>
                {formatDuration(minutos)}
              </option>
            ))}
          </Select>
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
