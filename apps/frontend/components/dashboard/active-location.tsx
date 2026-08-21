"use client";

import { Store } from "lucide-react";
import * as React from "react";

import { Select } from "@/components/ui/select";
import { listLocations, type Location } from "@/lib/api/locations";

/**
 * GUA-36 — Sede activa del dashboard.
 *
 * Casi todo lo operativo del negocio cuelga de una sede: los barberos, los
 * servicios, los horarios y la agenda. Una barberia con tres sedes tiene tres
 * equipos y tres listas de precios, asi que ninguna de esas pantallas puede
 * dibujarse sin saber "de cual". Esa eleccion se toma una vez, aqui, y la leen
 * todas con useActiveLocation().
 *
 * Se guarda en localStorage porque es una preferencia de trabajo, no un dato
 * del negocio: quien administra la sede del norte entra ahi todos los dias y
 * no tiene por que volver a elegirla en cada visita. No va en la URL para que
 * cambiar de sede no obligue a recargar la pagina ni ensucie cada ruta con un
 * parametro.
 *
 * Se cargan TODAS las sedes, tambien las cerradas. Una sede cerrada sigue
 * teniendo barberos, servicios e historial que el dueño necesita poder mirar,
 * y ademas hay que poder entrar en ella para volver a abrirla.
 */

const STORAGE_KEY = "guardao.activeLocationId";

interface ActiveLocationValue {
  /** Todas las sedes del negocio, abiertas y cerradas. */
  locations: Location[];
  activeLocation: Location | null;
  activeLocationId: string | null;
  selectLocation: (id: string) => void;
  /** Se vuelve a pedir la lista tras crear, editar o cerrar una sede. */
  reload: () => Promise<void>;
  loading: boolean;
  error: string | null;
}

const ActiveLocationContext = React.createContext<ActiveLocationValue | null>(null);

function leerGuardada(): string | null {
  if (typeof window === "undefined") return null;
  return window.localStorage.getItem(STORAGE_KEY);
}

export function ActiveLocationProvider({ children }: { children: React.ReactNode }) {
  const [locations, setLocations] = React.useState<Location[]>([]);
  const [activeLocationId, setActiveLocationId] = React.useState<string | null>(null);
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState<string | null>(null);

  // Cadena de promesas y no async/await con setLoading(true) al principio: el
  // estado solo se toca dentro de los callbacks, nunca de forma sincrona. Es
  // lo que exige react-hooks/set-state-in-effect, y con razon — un setState
  // sincrono en el cuerpo de un efecto provoca un render en cascada antes de
  // que la primera pintura llegue a la pantalla.
  const reload = React.useCallback(
    () =>
      listLocations()
        .then((sedes) => {
          setLocations(sedes);
          setError(null);

          // La guardada puede no servir ya: la borraron desde otra pestaña, o
          // es de la barberia con la que se entro antes en este mismo
          // navegador. En los dos casos se cae a la primera abierta, y a la
          // primera a secas si no hay ninguna abierta
          setActiveLocationId((actual) => {
            const candidata = actual ?? leerGuardada();
            if (candidata && sedes.some((sede) => sede.id === candidata)) {
              return candidata;
            }
            return sedes.find((sede) => sede.active)?.id ?? sedes[0]?.id ?? null;
          });
        })
        .catch(() => setError("No se pudieron cargar las sedes del negocio"))
        .finally(() => setLoading(false)),
    [],
  );

  React.useEffect(() => {
    void reload();
  }, [reload]);

  const selectLocation = React.useCallback((id: string) => {
    setActiveLocationId(id);
    window.localStorage.setItem(STORAGE_KEY, id);
  }, []);

  // Persistir tambien lo que elige reload() por su cuenta: si no, al entrar
  // por primera vez la sede se muestra elegida pero no queda guardada, y en la
  // visita siguiente vuelve a decidirse sola
  React.useEffect(() => {
    if (activeLocationId) {
      window.localStorage.setItem(STORAGE_KEY, activeLocationId);
    }
  }, [activeLocationId]);

  const value = React.useMemo<ActiveLocationValue>(
    () => ({
      locations,
      activeLocation: locations.find((sede) => sede.id === activeLocationId) ?? null,
      activeLocationId,
      selectLocation,
      reload,
      loading,
      error,
    }),
    [locations, activeLocationId, selectLocation, reload, loading, error],
  );

  return (
    <ActiveLocationContext.Provider value={value}>{children}</ActiveLocationContext.Provider>
  );
}

export function useActiveLocation(): ActiveLocationValue {
  const value = React.useContext(ActiveLocationContext);
  if (!value) {
    throw new Error("useActiveLocation solo se puede usar dentro de ActiveLocationProvider");
  }
  return value;
}

/**
 * El selector en si, para la cabecera del dashboard.
 *
 * Con una sola sede no se muestra un desplegable sino su nombre: elegir entre
 * una opcion no es elegir, y un control que no hace nada distrae. La mayoria
 * de las barberias empieza con una sola sede.
 */
export function LocationSwitcher() {
  const { locations, activeLocationId, selectLocation, loading } = useActiveLocation();
  const id = React.useId();

  if (loading && locations.length === 0) {
    return <span className="text-sm text-muted-foreground">Cargando sedes...</span>;
  }

  if (locations.length === 0) {
    return <span className="text-sm text-muted-foreground">Sin sedes</span>;
  }

  if (locations.length === 1) {
    return (
      <span className="flex items-center gap-1.5 text-sm font-medium">
        <Store aria-hidden className="size-3.5 text-muted-foreground" />
        {locations[0].name}
      </span>
    );
  }

  return (
    <div className="flex items-center gap-2">
      <label htmlFor={id} className="sr-only">
        Sede activa
      </label>
      <Store aria-hidden className="size-3.5 shrink-0 text-muted-foreground" />
      <Select
        id={id}
        value={activeLocationId ?? ""}
        onChange={(event) => selectLocation(event.target.value)}
        className="w-44"
      >
        {locations.map((sede) => (
          <option key={sede.id} value={sede.id}>
            {sede.name}
            {sede.active ? "" : " (cerrada)"}
          </option>
        ))}
      </Select>
    </div>
  );
}
