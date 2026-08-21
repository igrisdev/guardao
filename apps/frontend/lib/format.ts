/**
 * Formatos que se repiten en el dashboard.
 *
 * Estan aqui y no en cada pantalla porque un precio escrito de dos maneras
 * distintas en dos pantallas del mismo panel se lee como un error de datos,
 * no como una diferencia de estilo.
 */

const PESOS = new Intl.NumberFormat("es-CO", {
  style: "currency",
  currency: "COP",
  maximumFractionDigits: 0,
});

/**
 * Pesos colombianos sin decimales.
 *
 * El backend manda enteros en pesos, sin centavos (Tech Spec 3.1), asi que
 * aqui no hay division por 100 ni nada parecido: el numero que llega es el que
 * se muestra.
 */
export function formatPesos(pesos: number): string {
  return PESOS.format(pesos);
}

/**
 * Duracion en minutos, en lenguaje de barberia: "30 min", "1 h", "1 h 30 min".
 *
 * Siempre son multiplos de 30 (lo exige el backend), asi que no hay que
 * preocuparse por casos como "1 h 7 min".
 */
export function formatDuration(minutos: number): string {
  const horas = Math.floor(minutos / 60);
  const resto = minutos % 60;

  if (horas === 0) return `${resto} min`;
  if (resto === 0) return `${horas} h`;
  return `${horas} h ${resto} min`;
}

/** Las duraciones que ofrece el desplegable: de media hora a ocho horas. */
export const DURATION_OPTIONS = Array.from({ length: 16 }, (_, i) => (i + 1) * 30);

/** 0 = domingo, como en la base y como en Date.getDay(). */
export const DAY_NAMES = [
  "Domingo",
  "Lunes",
  "Martes",
  "Miercoles",
  "Jueves",
  "Viernes",
  "Sabado",
];

/**
 * Las horas que ofrecen los desplegables de horario: de 00:00 a 23:30, en
 * pasos de media hora.
 *
 * El backend rechaza cualquier hora fuera de esa rejilla, asi que el
 * formulario ofrece solo las validas en vez de dejar escribir y fallar
 * despues.
 */
export const HALF_HOURS = Array.from({ length: 48 }, (_, i) => {
  const hora = String(Math.floor(i / 2)).padStart(2, "0");
  const minuto = i % 2 === 0 ? "00" : "30";
  return `${hora}:${minuto}`;
});
