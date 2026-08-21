package com.guardao.backend.schedule;

import com.guardao.backend.business.LocationService;
import com.guardao.backend.shared.error.ApiException;
import com.guardao.backend.shared.error.ErrorCode;
import com.guardao.backend.staff.StaffService;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * GUA-33 — Horario semanal de una sede y de cada barbero.
 *
 * Guardar reemplaza el horario entero (ver ScheduleRequest). El borron y
 * cuenta nueva ocurre dentro de una transaccion, asi que una peticion que
 * falle la validacion no deja a la sede sin horario: o queda el nuevo
 * completo, o sigue el anterior intacto.
 *
 * La sede se verifica llamando a LocationService y el barbero a StaffService,
 * los servicios publicos de sus modulos, nunca sus repositorios (ADR-002).
 * Ambos responden 404 cuando la cosa no es del negocio del token, que es
 * exactamente la respuesta que corresponde aqui (ADR-004).
 */
@Service
public class ScheduleService {

    private final ScheduleRepository franjas;
    private final LocationService sedes;
    private final StaffService barberos;

    public ScheduleService(ScheduleRepository franjas, LocationService sedes,
            StaffService barberos) {
        this.franjas = franjas;
        this.sedes = sedes;
        this.barberos = barberos;
    }

    @Transactional(readOnly = true)
    public List<ScheduleSlotResponse> locationSchedule(UUID locationId) {
        sedes.get(locationId);

        return franjas.findByLocationIdAndStaffIdIsNullOrderByDayOfWeekAscOpenTimeAsc(locationId)
                .stream()
                .map(ScheduleSlotResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ScheduleSlotResponse> staffSchedule(UUID locationId, UUID staffId) {
        barberos.get(locationId, staffId);

        return franjas.findByLocationIdAndStaffIdOrderByDayOfWeekAscOpenTimeAsc(locationId, staffId)
                .stream()
                .map(ScheduleSlotResponse::from)
                .toList();
    }

    @Transactional
    public List<ScheduleSlotResponse> replaceLocationSchedule(UUID locationId,
            ScheduleRequest peticion) {
        sedes.get(locationId);
        validar(peticion.slots());

        franjas.deleteByLocationIdAndStaffIdIsNull(locationId);

        return guardar(locationId, null, peticion.slots());
    }

    @Transactional
    public List<ScheduleSlotResponse> replaceStaffSchedule(UUID locationId, UUID staffId,
            ScheduleRequest peticion) {
        barberos.get(locationId, staffId);
        validar(peticion.slots());

        franjas.deleteByLocationIdAndStaffId(locationId, staffId);

        return guardar(locationId, staffId, peticion.slots());
    }

    /**
     * Borra el horario propio del barbero, con lo que vuelve a regirse por el
     * de la sede.
     *
     * Es distinto de guardarle una lista vacia: sin franjas propias hereda el
     * horario general, mientras que una lista vacia seria un barbero que no
     * trabaja ningun dia. Los dos casos existen —el barbero normal y el que
     * esta de licencia larga— y necesitan operaciones distintas.
     */
    @Transactional
    public void clearStaffSchedule(UUID locationId, UUID staffId) {
        barberos.get(locationId, staffId);

        franjas.deleteByLocationIdAndStaffId(locationId, staffId);
    }

    private List<ScheduleSlotResponse> guardar(UUID locationId, UUID staffId,
            List<ScheduleSlotRequest> slots) {
        List<Schedule> nuevas = slots.stream()
                .map(slot -> new Schedule(locationId, staffId,
                        slot.dayOfWeek().shortValue(), slot.openTime(), slot.closeTime()))
                .toList();

        return franjas.saveAll(nuevas).stream()
                .sorted(Comparator.comparing(Schedule::getDayOfWeek)
                        .thenComparing(Schedule::getOpenTime))
                .map(ScheduleSlotResponse::from)
                .toList();
    }

    /**
     * Las dos reglas que no puede comprobar una anotacion sobre un campo,
     * porque miran mas de uno o mas de una franja.
     */
    private void validar(List<ScheduleSlotRequest> slots) {
        for (ScheduleSlotRequest slot : slots) {
            if (!slot.openTime().isBefore(slot.closeTime())) {
                throw new ApiException(ErrorCode.INVALID_TIME_RANGE,
                        "La hora de cierre debe ser posterior a la de apertura");
            }
        }

        rechazarCruces(slots);
    }

    /**
     * Dos franjas del mismo dia no pueden cruzarse.
     *
     * Si se cruzaran, el mismo hueco saldria dos veces en la disponibilidad y
     * el cliente veria las 10:00 repetidas. Se comparan solo entre si las del
     * mismo dia: que el lunes y el martes "se crucen" en hora es lo normal.
     *
     * Tocarse no es cruzarse: de 8 a 12 y de 12 a 18 es una jornada seguida
     * partida en dos filas, cosa que la interfaz puede producir sin querer y
     * que no rompe nada.
     */
    private void rechazarCruces(List<ScheduleSlotRequest> slots) {
        Map<Integer, List<ScheduleSlotRequest>> porDia = slots.stream()
                .collect(Collectors.groupingBy(ScheduleSlotRequest::dayOfWeek));

        for (List<ScheduleSlotRequest> delDia : porDia.values()) {
            List<ScheduleSlotRequest> ordenadas = new ArrayList<>(delDia);
            ordenadas.sort(Comparator.comparing(ScheduleSlotRequest::openTime));

            for (int i = 1; i < ordenadas.size(); i++) {
                LocalTime cierreAnterior = ordenadas.get(i - 1).closeTime();
                LocalTime aperturaActual = ordenadas.get(i).openTime();

                if (aperturaActual.isBefore(cierreAnterior)) {
                    throw new ApiException(ErrorCode.OVERLAPPING_SCHEDULE,
                            "Las franjas del dia " + ordenadas.get(i).dayOfWeek()
                                    + " se cruzan entre si");
                }
            }
        }
    }
}
