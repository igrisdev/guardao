"use client";

import { Check, MapPin, Pencil, Plus, Power, Store } from "lucide-react";
import * as React from "react";

import { useActiveLocation } from "@/components/dashboard/active-location";
import { Field } from "@/components/dashboard/field";
import { SectionHeader } from "@/components/dashboard/section-header";
import { Alert } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import {
  createLocation,
  deactivateLocation,
  reactivateLocation,
  updateLocation,
  type Location,
} from "@/lib/api/locations";
import { fieldErrors, formErrorMessage } from "@/lib/api/form-errors";

/**
 * GUA-36 — Sedes del negocio y eleccion de la sede activa.
 *
 * La lista sale del mismo contexto que alimenta el selector de la cabecera, no
 * de una peticion propia. Es lo que hace que crear una sede aqui la haga
 * aparecer arriba al instante, y que cerrarla la saque de las dos partes a la
 * vez: con dos copias de la misma lista, una se queda vieja y nadie sabe cual.
 *
 * Cerrar no borra. El backend desactiva la sede porque su historial de citas
 * cuelga de ella, y por eso el boton dice "Cerrar" y no "Eliminar": prometer
 * un borrado que no ocurre es peor que explicar lo que si pasa.
 */
export default function ConfigLocationsPage() {
  const { locations, activeLocationId, selectLocation, reload, loading, error } =
    useActiveLocation();

  const [editando, setEditando] = React.useState<Location | null>(null);
  const [creando, setCreando] = React.useState(false);
  const [errorAccion, setErrorAccion] = React.useState<string | null>(null);
  const [enCurso, setEnCurso] = React.useState<string | null>(null);

  const activas = locations.filter((sede) => sede.active).length;

  async function alternarSede(sede: Location) {
    setErrorAccion(null);
    setEnCurso(sede.id);
    try {
      if (sede.active) {
        await deactivateLocation(sede.id);
      } else {
        await reactivateLocation(sede.id);
      }
      await reload();
    } catch (err) {
      setErrorAccion(formErrorMessage(err) ?? "No se pudo cambiar el estado de la sede");
    } finally {
      setEnCurso(null);
    }
  }

  return (
    <div className="flex flex-col gap-5">
      <SectionHeader
        icon={Store}
        title="Sedes"
        description="Cada sede tiene su propio equipo, sus servicios y su horario."
        action={
          <Button onClick={() => setCreando(true)}>
            <Plus data-icon="inline-start" />
            Nueva sede
          </Button>
        }
      />

      {error ? <Alert>{error}</Alert> : null}
      {errorAccion ? <Alert>{errorAccion}</Alert> : null}

      {loading && locations.length === 0 ? (
        <p className="text-sm text-muted-foreground">Cargando sedes...</p>
      ) : null}

      <ul className="flex flex-col gap-3">
        {locations.map((sede) => {
          const esActiva = sede.id === activeLocationId;

          return (
            <li
              key={sede.id}
              className={`rounded-xl border bg-card p-4 ${
                esActiva ? "border-primary" : "border-border"
              }`}
            >
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div className="min-w-0">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="font-medium">{sede.name}</span>
                    {sede.active ? null : <Badge variant="inactive">Cerrada</Badge>}
                    {esActiva ? (
                      <Badge variant="accent">
                        <Check aria-hidden className="size-3" />
                        Sede activa
                      </Badge>
                    ) : null}
                  </div>
                  {sede.address || sede.city ? (
                    <p className="mt-1 flex items-center gap-1.5 text-[13px] text-muted-foreground">
                      <MapPin aria-hidden className="size-3.5 shrink-0" />
                      {[sede.address, sede.city].filter(Boolean).join(", ")}
                    </p>
                  ) : (
                    <p className="mt-1 text-[13px] text-muted-foreground">Sin direccion</p>
                  )}
                </div>

                <div className="flex shrink-0 flex-wrap gap-2">
                  {esActiva ? null : (
                    <Button variant="outline" size="sm" onClick={() => selectLocation(sede.id)}>
                      Trabajar en esta
                    </Button>
                  )}
                  <Button variant="ghost" size="sm" onClick={() => setEditando(sede)}>
                    <Pencil data-icon="inline-start" />
                    Editar
                  </Button>
                  <Button
                    variant={sede.active ? "destructive" : "outline"}
                    size="sm"
                    disabled={enCurso === sede.id || (sede.active && activas <= 1)}
                    // Con una sola sede abierta el backend responde 409: el
                    // negocio se quedaria sin nada que ofrecer. Se deshabilita
                    // aqui tambien para no hacer viajar una peticion cuyo
                    // resultado ya se sabe
                    title={
                      sede.active && activas <= 1
                        ? "Es la unica sede abierta del negocio"
                        : undefined
                    }
                    onClick={() => void alternarSede(sede)}
                  >
                    <Power data-icon="inline-start" />
                    {sede.active ? "Cerrar" : "Reabrir"}
                  </Button>
                </div>
              </div>
            </li>
          );
        })}
      </ul>

      {!loading && locations.length === 0 ? (
        <p className="rounded-lg border border-dashed border-border p-8 text-center text-sm text-muted-foreground">
          Este negocio todavia no tiene sedes.
        </p>
      ) : null}

      {/* La ventana se monta solo cuando hace falta, y la key la vuelve a
          montar al cambiar de sede. Es lo que reinicia los campos sin un
          efecto que los reescriba: en React, para resetear el estado de un
          componente se cambia su key */}
      {creando || editando ? (
        <LocationDialog
          key={editando?.id ?? "nueva"}
          location={editando}
          onClose={() => {
            setCreando(false);
            setEditando(null);
          }}
          onSaved={reload}
        />
      ) : null}
    </div>
  );
}

/**
 * Formulario de alta y de edicion en una sola ventana.
 *
 * Son el mismo formulario porque piden exactamente los mismos campos; lo unico
 * que cambia es a donde va la peticion. Dos componentes casi iguales se
 * separan con el tiempo, y uno termina validando algo que el otro no.
 *
 * No incluye si la sede esta abierta: eso se cambia con su propio boton, para
 * que corregir una direccion no pueda cerrar una sede sin querer.
 */
function LocationDialog({
  location,
  onClose,
  onSaved,
}: {
  location: Location | null;
  onClose: () => void;
  onSaved: () => Promise<void>;
}) {
  // Los campos arrancan con lo que tenga la sede que se esta editando, o
  // vacios al crear una. Va en el valor inicial de useState y no en un efecto
  // porque el componente se monta de cero cada vez (ver la key de arriba)
  const [name, setName] = React.useState(location?.name ?? "");
  const [address, setAddress] = React.useState(location?.address ?? "");
  const [city, setCity] = React.useState(location?.city ?? "");
  const [errores, setErrores] = React.useState<Record<string, string>>({});
  const [errorGeneral, setErrorGeneral] = React.useState<string | null>(null);
  const [guardando, setGuardando] = React.useState(false);

  async function guardar(event: React.FormEvent) {
    event.preventDefault();
    setGuardando(true);
    setErrores({});
    setErrorGeneral(null);

    // El backend guarda address y city como nulos cuando no vienen; una cadena
    // vacia dejaria una direccion en blanco que despues hay que limpiar
    const payload = {
      name: name.trim(),
      address: address.trim() || null,
      city: city.trim() || null,
    };

    try {
      if (location) {
        await updateLocation(location.id, payload);
      } else {
        await createLocation(payload);
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
      title={location ? "Editar sede" : "Nueva sede"}
      description="El nombre es lo que veran tus clientes al elegir donde reservar."
    >
      <form onSubmit={guardar} className="flex flex-col gap-4">
        {errorGeneral ? <Alert>{errorGeneral}</Alert> : null}

        <Field id="sede-nombre" label="Nombre" error={errores.name}>
          <Input
            id="sede-nombre"
            value={name}
            onChange={(event) => setName(event.target.value)}
            placeholder="Sede Norte"
            maxLength={120}
            required
            aria-invalid={Boolean(errores.name)}
          />
        </Field>

        <Field id="sede-direccion" label="Direccion" error={errores.address}>
          <Input
            id="sede-direccion"
            value={address}
            onChange={(event) => setAddress(event.target.value)}
            placeholder="Calle 10 # 5-20"
            maxLength={200}
            aria-invalid={Boolean(errores.address)}
          />
        </Field>

        <Field id="sede-ciudad" label="Ciudad" error={errores.city}>
          <Input
            id="sede-ciudad"
            value={city}
            onChange={(event) => setCity(event.target.value)}
            placeholder="Cali"
            maxLength={80}
            aria-invalid={Boolean(errores.city)}
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
