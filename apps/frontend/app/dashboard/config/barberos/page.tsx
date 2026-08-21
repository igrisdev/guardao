"use client";

import { KeyRound, Pencil, Plus, Power, Users } from "lucide-react";
import * as React from "react";

import { useActiveLocation } from "@/components/dashboard/active-location";
import { Field } from "@/components/dashboard/field";
import { NoLocationSelected } from "@/components/dashboard/no-location";
import { SectionHeader } from "@/components/dashboard/section-header";
import { Alert } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { fieldErrors, formErrorMessage } from "@/lib/api/form-errors";
import {
  createStaff,
  createStaffAccount,
  deactivateStaff,
  listStaff,
  reactivateStaff,
  updateStaff,
  type Staff,
} from "@/lib/api/staff";

/**
 * GUA-37 — Barberos de la sede activa, y su acceso al dashboard.
 *
 * Son dos cosas distintas y la pantalla lo refleja: primero se crea la ficha
 * del barbero, y darle acceso es un paso aparte. Hay barberias donde la agenda
 * la maneja solo el mostrador y ningun barbero entra al sistema; obligar a
 * inventarle un correo a cada uno seria pedir datos que no existen.
 *
 * Dar de baja no borra. El backend desactiva al barbero porque sus citas
 * atendidas lo referencian y los informes por barbero se apoyan en ellas.
 */
export default function ConfigStaffPage() {
  const { activeLocationId, activeLocation } = useActiveLocation();

  if (!activeLocationId) {
    return (
      <>
        <SectionHeader icon={Users} title="Barberos" />
        <NoLocationSelected what="barberos" />
      </>
    );
  }

  // La key remonta la pantalla al cambiar de sede. Es lo que hace que el
  // selector de arriba cambie de verdad lo que se ve: sin ella, la lista
  // vieja se quedaria en pantalla mientras llega la nueva, y cualquier
  // formulario abierto seguiria apuntando a la sede anterior
  return (
    <StaffList
      key={activeLocationId}
      locationId={activeLocationId}
      locationName={activeLocation?.name ?? ""}
    />
  );
}

function StaffList({ locationId, locationName }: { locationId: string; locationName: string }) {
  const [barberos, setBarberos] = React.useState<Staff[]>([]);
  const [cargando, setCargando] = React.useState(true);
  const [error, setError] = React.useState<string | null>(null);
  const [enCurso, setEnCurso] = React.useState<string | null>(null);

  const [editando, setEditando] = React.useState<Staff | null>(null);
  const [creando, setCreando] = React.useState(false);
  const [dandoAcceso, setDandoAcceso] = React.useState<Staff | null>(null);

  // Cadena de promesas, sin ningun setState antes de la primera llamada: es lo
  // que pide react-hooks/set-state-in-effect para poder llamarla desde el
  // efecto de abajo sin provocar un render en cascada
  const cargar = React.useCallback(
    () =>
      listStaff(locationId)
        .then((lista) => {
          setBarberos(lista);
          setError(null);
        })
        .catch((err) => setError(formErrorMessage(err) ?? "No se pudieron cargar los barberos"))
        .finally(() => setCargando(false)),
    [locationId],
  );

  React.useEffect(() => {
    void cargar();
  }, [cargar]);

  async function alternar(barbero: Staff) {
    setError(null);
    setEnCurso(barbero.id);
    try {
      if (barbero.active) {
        await deactivateStaff(locationId, barbero.id);
      } else {
        await reactivateStaff(locationId, barbero.id);
      }
      await cargar();
    } catch (err) {
      setError(formErrorMessage(err) ?? "No se pudo cambiar el estado del barbero");
    } finally {
      setEnCurso(null);
    }
  }

  return (
    <div className="flex flex-col gap-5">
      <SectionHeader
        icon={Users}
        title="Barberos"
        description={`Quien atiende en ${locationName || "esta sede"}.`}
        action={
          <Button onClick={() => setCreando(true)}>
            <Plus data-icon="inline-start" />
            Nuevo barbero
          </Button>
        }
      />

      {error ? <Alert>{error}</Alert> : null}
      {cargando ? <p className="text-sm text-muted-foreground">Cargando barberos...</p> : null}

      <ul className="flex flex-col gap-3">
        {barberos.map((barbero) => (
          <li key={barbero.id} className="rounded-xl border border-border bg-card p-4">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div className="flex items-center gap-2">
                <span className="font-medium">{barbero.name}</span>
                {barbero.active ? null : <Badge variant="inactive">Dado de baja</Badge>}
              </div>

              <div className="flex shrink-0 flex-wrap gap-2">
                <Button variant="outline" size="sm" onClick={() => setDandoAcceso(barbero)}>
                  <KeyRound data-icon="inline-start" />
                  Crear acceso
                </Button>
                <Button variant="ghost" size="sm" onClick={() => setEditando(barbero)}>
                  <Pencil data-icon="inline-start" />
                  Editar
                </Button>
                <Button
                  variant={barbero.active ? "destructive" : "outline"}
                  size="sm"
                  disabled={enCurso === barbero.id}
                  onClick={() => void alternar(barbero)}
                >
                  <Power data-icon="inline-start" />
                  {barbero.active ? "Dar de baja" : "Reactivar"}
                </Button>
              </div>
            </div>
          </li>
        ))}
      </ul>

      {!cargando && barberos.length === 0 ? (
        <p className="rounded-lg border border-dashed border-border p-8 text-center text-sm text-muted-foreground">
          Esta sede todavia no tiene barberos. Agrega el primero para poder crear su horario y
          empezar a recibir citas.
        </p>
      ) : null}

      {/* Se montan solo cuando hacen falta y con key: es lo que reinicia sus
          campos al pasar de un barbero a otro, sin un efecto que los reescriba */}
      {creando || editando ? (
        <StaffDialog
          key={editando?.id ?? "nuevo"}
          locationId={locationId}
          staff={editando}
          onClose={() => {
            setCreando(false);
            setEditando(null);
          }}
          onSaved={cargar}
        />
      ) : null}

      {dandoAcceso ? (
        <StaffAccountDialog
          key={dandoAcceso.id}
          staff={dandoAcceso}
          onClose={() => setDandoAcceso(null)}
        />
      ) : null}
    </div>
  );
}

/** Alta y edicion en el mismo formulario: piden lo mismo, un nombre. */
function StaffDialog({
  locationId,
  staff,
  onClose,
  onSaved,
}: {
  locationId: string;
  staff: Staff | null;
  onClose: () => void;
  onSaved: () => Promise<void>;
}) {
  const [name, setName] = React.useState(staff?.name ?? "");
  const [errores, setErrores] = React.useState<Record<string, string>>({});
  const [errorGeneral, setErrorGeneral] = React.useState<string | null>(null);
  const [guardando, setGuardando] = React.useState(false);

  async function guardar(event: React.FormEvent) {
    event.preventDefault();
    setGuardando(true);
    setErrores({});
    setErrorGeneral(null);

    try {
      if (staff) {
        await updateStaff(locationId, staff.id, { name: name.trim() });
      } else {
        await createStaff(locationId, { name: name.trim() });
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
      title={staff ? "Editar barbero" : "Nuevo barbero"}
      description="Solo la ficha. El acceso al sistema se crea aparte, y solo si lo necesita."
    >
      <form onSubmit={guardar} className="flex flex-col gap-4">
        {errorGeneral ? <Alert>{errorGeneral}</Alert> : null}

        <Field id="barbero-nombre" label="Nombre" error={errores.name}>
          <Input
            id="barbero-nombre"
            value={name}
            onChange={(event) => setName(event.target.value)}
            placeholder="Andres Mesa"
            maxLength={120}
            required
            aria-invalid={Boolean(errores.name)}
            aria-describedby={errores.name ? "barbero-nombre-error" : undefined}
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

/**
 * Le crea el login a un barbero (GUA-23).
 *
 * La contraseña la elige el dueño y se la entrega al barbero; hoy no hay
 * pantalla de perfil donde el barbero pueda cambiarla despues, y conviene
 * decirlo en el formulario en vez de que se descubra al usarlo.
 *
 * No se consulta antes si el barbero ya tiene acceso porque no hay endpoint
 * que lo diga: el backend expone crear y nada mas. Si ya lo tenia responde 409
 * y eso es lo que se muestra, que informa lo mismo con una peticion menos.
 */
function StaffAccountDialog({ staff, onClose }: { staff: Staff; onClose: () => void }) {
  const [email, setEmail] = React.useState("");
  const [password, setPassword] = React.useState("");
  const [errores, setErrores] = React.useState<Record<string, string>>({});
  const [errorGeneral, setErrorGeneral] = React.useState<string | null>(null);
  const [guardando, setGuardando] = React.useState(false);
  const [creado, setCreado] = React.useState<string | null>(null);

  async function guardar(event: React.FormEvent) {
    event.preventDefault();
    setGuardando(true);
    setErrores({});
    setErrorGeneral(null);

    try {
      const cuenta = await createStaffAccount({
        staffId: staff.id,
        email: email.trim(),
        password,
      });
      setCreado(cuenta.email);
      // La contraseña deja de hacer falta en cuanto el backend la acepta, y no
      // tiene por que seguir en memoria mientras el aviso este en pantalla
      setPassword("");
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
      title="Crear acceso"
      description={`${staff.name} podra entrar al dashboard con este correo y ver lo que le corresponde a su rol.`}
    >
      {creado ? (
        <div className="flex flex-col gap-4">
          <p className="rounded-lg border border-border bg-secondary px-3 py-2 text-sm">
            Acceso creado para <span className="font-medium">{creado}</span>. Entregale la
            contraseña que elegiste; no se puede volver a consultar desde aqui.
          </p>
          <div className="flex justify-end">
            <Button type="button" onClick={onClose}>
              Listo
            </Button>
          </div>
        </div>
      ) : (
        <form onSubmit={guardar} className="flex flex-col gap-4">
          {errorGeneral ? <Alert>{errorGeneral}</Alert> : null}

          <Field id="acceso-correo" label="Correo" error={errores.email}>
            <Input
              id="acceso-correo"
              type="email"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              placeholder="andres@elcorte.co"
              maxLength={180}
              required
              autoComplete="off"
              aria-invalid={Boolean(errores.email)}
            />
          </Field>

          <Field
            id="acceso-clave"
            label="Contraseña inicial"
            error={errores.password}
            hint="Minimo 8 caracteres. Se la entregas tu al barbero."
          >
            <Input
              id="acceso-clave"
              type="password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              minLength={8}
              maxLength={72}
              required
              autoComplete="new-password"
              aria-invalid={Boolean(errores.password)}
            />
          </Field>

          <div className="mt-1 flex justify-end gap-2">
            <Button type="button" variant="outline" onClick={onClose}>
              Cancelar
            </Button>
            <Button type="submit" disabled={guardando}>
              {guardando ? "Creando..." : "Crear acceso"}
            </Button>
          </div>
        </form>
      )}
    </Dialog>
  );
}
