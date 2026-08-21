package com.guardao.backend.schedule;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * GUA-35 — Los ratos que un barbero ya tiene comprometidos con citas.
 *
 * Va contra la tabla appointment con SQL directo, y eso necesita explicacion.
 * La entidad Appointment y su modulo (booking) son de la Etapa 3; todavia no
 * existen. El motor de disponibilidad, en cambio, es de esta etapa y no sirve
 * de nada si ofrece horas que ya estan tomadas. Las dos opciones eran mapear
 * aqui una entidad de otro modulo —y chocar con la que cree la Etapa 3 cuando
 * llegue, porque Hibernate no admite dos entidades sobre la misma tabla— o
 * leer las dos columnas que hacen falta y nada mas. Esto segundo es lo que
 * menos estorba despues.
 *
 * QUE HACER EN LA ETAPA 3: cuando exista el modulo booking, esta clase se
 * reemplaza por una llamada a su servicio publico (ADR-002) y se borra. La
 * firma del metodo esta pensada para que ese cambio sea de una linea en
 * AvailabilityService.
 *
 * OJO CON EL AISLAMIENTO: el SQL directo NO pasa por el filtro de negocio de
 * Hibernate, que solo actua sobre consultas de entidades. Aqui la unica
 * defensa es que staffId ya viene verificado contra la sede y el negocio del
 * token antes de llegar (ADR-004). No llamar a este metodo con un staffId que
 * no se haya comprobado antes.
 */
@Repository
public class BookedSlotQuery {

    /**
     * Solo PENDING y CONFIRMED ocupan agenda. Las canceladas y las no
     * asistidas liberan el horario, y es el mismo criterio del WHERE de
     * appointment_no_overlap: si aqui se contara una cancelada, el hueco
     * quedaria bloqueado para siempre sin que nadie entienda por que.
     */
    private static final String SQL = """
            select scheduled_at, ends_at
              from appointment
             where staff_id = ?
               and status in ('PENDING', 'CONFIRMED')
               and scheduled_at < ?
               and ends_at > ?
             order by scheduled_at
            """;

    private final JdbcTemplate jdbc;

    public BookedSlotQuery(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Las citas del barbero que pisan el rango.
     *
     * La condicion es "empieza antes de que termine el rango y termina despues
     * de que empiece", no "esta contenida en el rango": una cita que arranco
     * ayer a las 23:00 y termina hoy a la 1:00 ocupa la primera hora de hoy.
     */
    List<TimeRange> forStaffBetween(UUID staffId, Instant desde, Instant hasta) {
        return jdbc.query(SQL,
                (rs, fila) -> new TimeRange(
                        // getObject con el tipo explicito y no getTimestamp: la
                        // segunda aplica la zona horaria de la JVM al convertir,
                        // asi que el mismo dato sale distinto segun donde corra
                        rs.getObject(1, OffsetDateTime.class).toInstant(),
                        rs.getObject(2, OffsetDateTime.class).toInstant()),
                staffId,
                OffsetDateTime.ofInstant(hasta, ZoneOffset.UTC),
                OffsetDateTime.ofInstant(desde, ZoneOffset.UTC));
    }
}
