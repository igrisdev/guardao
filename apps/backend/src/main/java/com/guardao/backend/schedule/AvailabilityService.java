package com.guardao.backend.schedule;

import com.guardao.backend.shared.error.ApiException;
import com.guardao.backend.shared.error.ErrorCode;
import com.guardao.backend.staff.ServiceCatalogService;
import com.guardao.backend.staff.ServiceResponse;
import com.guardao.backend.staff.SkillService;
import com.guardao.backend.staff.StaffResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * GUA-35 — Que huecos quedan libres, cruzando todo lo que puede ocuparlos.
 *
 * Este servicio reune los datos; la cuenta la hace AvailabilityCalculator, que
 * no sabe de base de datos. La separacion es deliberada: asi los casos limite
 * del horario se prueban sin levantar Postgres (GUA-40).
 *
 * Cuatro cosas se cruzan para cada barbero y cada dia:
 *
 *   1. el horario general de la sede — cuando esta abierto el local
 *   2. el horario propio del barbero, si lo tiene — cuando viene el
 *   3. sus bloqueos — vacaciones, una cita medica (GUA-34)
 *   4. sus citas ya agendadas — lo unico que no vive en este modulo todavia
 *
 * Y una quinta que no es un rato sino un permiso: solo se consideran los
 * barberos que saben hacer ese servicio (GUA-32). Un barbero que no hace
 * tinturados no debe aparecer entre las opciones de un tinturado, por libre
 * que tenga la tarde. Si nadie tiene la habilidad asignada la respuesta sale
 * vacia; no es un error, es una barberia a la que le falta configurar quien
 * hace que.
 */
@Service
public class AvailabilityService {

    /** La rejilla de la agenda. Ver el javadoc de AvailabilityCalculator. */
    private static final Duration PASO = Duration.ofMinutes(30);

    /**
     * Tope del rango consultable. Sin tope, un "del 2026 al 2030" recorre
     * miles de dias con una consulta de bloqueos y otra de citas por barbero y
     * por dia. Dos meses cubren de sobra lo que alguien quiere ver de una vez.
     */
    private static final int MAX_DIAS = 62;

    private final ScheduleRepository franjas;
    private final BlockRepository bloqueos;
    private final BookedSlotQuery citas;
    private final ServiceCatalogService servicios;
    private final SkillService habilidades;
    private final ZoneId zona;

    public AvailabilityService(ScheduleRepository franjas, BlockRepository bloqueos,
            BookedSlotQuery citas, ServiceCatalogService servicios, SkillService habilidades,
            @Value("${guardao.timezone}") String timezone) {
        this.franjas = franjas;
        this.bloqueos = bloqueos;
        this.citas = citas;
        this.servicios = servicios;
        this.habilidades = habilidades;
        this.zona = ZoneId.of(timezone);
    }

    @Transactional(readOnly = true)
    public AvailabilityResponse find(UUID locationId, UUID serviceId, LocalDate desde,
            LocalDate hasta, UUID staffId) {

        validarRango(desde, hasta);

        // Comprueba de paso que el servicio sea de esa sede y la sede del
        // negocio del token: responde 404 si no (ADR-004)
        ServiceResponse servicio = servicios.get(locationId, serviceId);
        Duration duracion = Duration.ofMinutes(servicio.durationMin());

        List<StaffResponse> candidatos = candidatos(locationId, serviceId, staffId);

        // El limite inferior se fija una sola vez para toda la respuesta: si se
        // tomara la hora dentro del bucle, un calculo lento podria ofrecer un
        // hueco en el primer dia y descartar el equivalente en el ultimo
        Instant ahora = Instant.now();

        List<Schedule> horarioSede =
                franjas.findByLocationIdAndStaffIdIsNullOrderByDayOfWeekAscOpenTimeAsc(locationId);

        List<AvailabilityDayResponse> dias = new ArrayList<>();
        for (LocalDate dia = desde; !dia.isAfter(hasta); dia = dia.plusDays(1)) {
            dias.add(new AvailabilityDayResponse(dia,
                    huecosDelDia(dia, candidatos, horarioSede, locationId, duracion, ahora)));
        }

        return new AvailabilityResponse(locationId, serviceId, servicio.name(),
                servicio.durationMin(), dias);
    }

    /**
     * Los barberos que pueden atender ese servicio: activos y con la habilidad
     * asignada. Si se pidio uno concreto, se comprueba que cumpla las dos
     * cosas en vez de darlo por bueno.
     */
    private List<StaffResponse> candidatos(UUID locationId, UUID serviceId, UUID staffId) {
        List<StaffResponse> conLaHabilidad = habilidades.staffOfService(locationId, serviceId)
                .stream()
                .filter(StaffResponse::active)
                .toList();

        if (staffId == null) {
            return conLaHabilidad;
        }

        return conLaHabilidad.stream()
                .filter(barbero -> barbero.id().equals(staffId))
                .toList();
    }

    private List<AvailableSlotResponse> huecosDelDia(LocalDate dia, List<StaffResponse> candidatos,
            List<Schedule> horarioSede, UUID locationId, Duration duracion, Instant ahora) {

        short numeroDeDia = DayOfWeekCodec.toDatabase(dia.getDayOfWeek());
        List<TimeRange> abiertoLaSede = ratosDe(horarioSede, dia, numeroDeDia);

        List<AvailableSlotResponse> huecos = new ArrayList<>();

        for (StaffResponse barbero : candidatos) {
            List<Schedule> horarioPropio = franjas
                    .findByLocationIdAndStaffIdOrderByDayOfWeekAscOpenTimeAsc(
                            locationId, barbero.id());

            // Distinguir "no tiene horario propio" de "ese dia no trabaja" es
            // lo que decide si hereda el de la sede. Por eso se mira si tiene
            // franjas en toda la semana, no solo en este dia: un barbero que
            // solo declaro los sabados no trabaja los lunes, aunque la sede
            // abra
            List<TimeRange> abierto = AvailabilityCalculator.restrictTo(
                    abiertoLaSede,
                    ratosDe(horarioPropio, dia, numeroDeDia),
                    !horarioPropio.isEmpty());

            if (abierto.isEmpty()) {
                continue;
            }

            List<TimeRange> ocupado = ocupado(barbero.id(), ventanaDe(abierto));

            for (Instant inicio : AvailabilityCalculator.freeStarts(
                    abierto, ocupado, duracion, PASO, ahora)) {
                huecos.add(new AvailableSlotResponse(
                        inicio, inicio.plus(duracion), barbero.id(), barbero.name()));
            }
        }

        huecos.sort(Comparator.comparing(AvailableSlotResponse::startAt)
                .thenComparing(AvailableSlotResponse::staffName));

        return huecos;
    }

    /**
     * Convierte las franjas de ese dia de la semana en ratos concretos de esa
     * fecha.
     *
     * Aqui es donde la hora local de la sede se vuelve un instante. "Abrimos a
     * las 8" se guarda sin zona a proposito (Tech Spec 3.1); las 8 de la
     * mañana del 3 de septiembre en Bogota si es un momento unico, y es con
     * ese con el que hay que comparar bloqueos y citas, que van en timestamptz.
     */
    private List<TimeRange> ratosDe(List<Schedule> horario, LocalDate dia, short numeroDeDia) {
        return horario.stream()
                .filter(franja -> franja.getDayOfWeek() == numeroDeDia)
                .map(franja -> new TimeRange(
                        dia.atTime(franja.getOpenTime()).atZone(zona).toInstant(),
                        dia.atTime(franja.getCloseTime()).atZone(zona).toInstant()))
                .toList();
    }

    /** Bloqueos y citas del barbero que pisan la ventana abierta de ese dia. */
    private List<TimeRange> ocupado(UUID staffId, TimeRange ventana) {
        List<TimeRange> ocupado = new ArrayList<>(
                bloqueos.findByStaffIdAndStartAtLessThanAndEndAtGreaterThanOrderByStartAtAsc(
                                staffId, ventana.endAt(), ventana.startAt())
                        .stream()
                        .map(bloqueo -> new TimeRange(bloqueo.getStartAt(), bloqueo.getEndAt()))
                        .toList());

        ocupado.addAll(citas.forStaffBetween(staffId, ventana.startAt(), ventana.endAt()));

        return ocupado;
    }

    /**
     * De la primera apertura al ultimo cierre del dia. Es el rango con el que
     * se piden bloqueos y citas: una consulta por barbero y dia en vez de una
     * por franja. Con jornada partida cubre tambien el rato del almuerzo, cosa
     * que no estorba porque ahi no hay ningun hueco que ofrecer.
     */
    private TimeRange ventanaDe(List<TimeRange> abierto) {
        Instant inicio = abierto.stream().map(TimeRange::startAt).min(Instant::compareTo)
                .orElseThrow();
        Instant fin = abierto.stream().map(TimeRange::endAt).max(Instant::compareTo)
                .orElseThrow();

        return new TimeRange(inicio, fin);
    }

    private void validarRango(LocalDate desde, LocalDate hasta) {
        if (hasta.isBefore(desde)) {
            throw new ApiException(ErrorCode.INVALID_TIME_RANGE,
                    "La fecha final debe ser posterior o igual a la inicial");
        }

        if (desde.datesUntil(hasta.plusDays(1)).count() > MAX_DIAS) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "El rango no puede pasar de " + MAX_DIAS + " dias");
        }
    }
}
