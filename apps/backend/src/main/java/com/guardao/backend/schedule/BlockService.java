package com.guardao.backend.schedule;

import com.guardao.backend.shared.error.ApiException;
import com.guardao.backend.shared.error.ErrorCode;
import com.guardao.backend.staff.StaffService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * GUA-34 — Bloqueos de agenda de un barbero.
 *
 * A diferencia de barberos y servicios, un bloqueo SI se borra de verdad. No
 * tiene historial que conservar: cuando unas vacaciones se cancelan, lo
 * correcto es que ese rato vuelva a estar libre y no quede rastro. Nada lo
 * referencia con clave foranea, asi que borrarlo no arrastra nada.
 *
 * El barbero se verifica con StaffService, el servicio publico de su modulo
 * (ADR-002), que ya responde 404 si el barbero no es de esa sede o la sede no
 * es del negocio del token (ADR-004).
 */
@Service
public class BlockService {

    private final BlockRepository bloqueos;
    private final StaffService barberos;

    public BlockService(BlockRepository bloqueos, StaffService barberos) {
        this.bloqueos = bloqueos;
        this.barberos = barberos;
    }

    @Transactional
    public BlockResponse create(UUID locationId, UUID staffId, BlockRequest peticion) {
        barberos.get(locationId, staffId);
        validarRango(peticion.startAt(), peticion.endAt());

        Block bloqueo = new Block(staffId, peticion.startAt(), peticion.endAt(),
                peticion.reason());

        return BlockResponse.from(bloqueos.save(bloqueo));
    }

    @Transactional(readOnly = true)
    public List<BlockResponse> list(UUID locationId, UUID staffId) {
        barberos.get(locationId, staffId);

        return bloqueos.findByStaffIdOrderByStartAtAsc(staffId).stream()
                .map(BlockResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public BlockResponse get(UUID locationId, UUID staffId, UUID blockId) {
        return BlockResponse.from(buscar(locationId, staffId, blockId));
    }

    @Transactional
    public BlockResponse update(UUID locationId, UUID staffId, UUID blockId,
            BlockRequest peticion) {
        Block bloqueo = buscar(locationId, staffId, blockId);
        validarRango(peticion.startAt(), peticion.endAt());

        bloqueo.setStartAt(peticion.startAt());
        bloqueo.setEndAt(peticion.endAt());
        bloqueo.setReason(peticion.reason());

        return BlockResponse.from(bloqueo);
    }

    @Transactional
    public void delete(UUID locationId, UUID staffId, UUID blockId) {
        bloqueos.delete(buscar(locationId, staffId, blockId));
    }

    private Block buscar(UUID locationId, UUID staffId, UUID blockId) {
        barberos.get(locationId, staffId);

        return bloqueos.findByIdAndStaffId(blockId, staffId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND,
                        "Ese bloqueo no existe para este barbero"));
    }

    /**
     * La base tambien lo exige con block_start_before_end. Aqui se comprueba
     * antes para responder un 400 que dice cual es el problema, en vez del 409
     * generico en que se traduce una violacion de integridad.
     */
    private void validarRango(Instant desde, Instant hasta) {
        if (!desde.isBefore(hasta)) {
            throw new ApiException(ErrorCode.INVALID_TIME_RANGE,
                    "El fin del bloqueo debe ser posterior a su inicio");
        }
    }
}
