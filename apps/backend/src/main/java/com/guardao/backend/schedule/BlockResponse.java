package com.guardao.backend.schedule;

import java.time.Instant;
import java.util.UUID;

/** GUA-34 — Bloqueo tal como lo ve el dashboard. */
public record BlockResponse(
        UUID id,
        UUID staffId,
        Instant startAt,
        Instant endAt,
        String reason) {

    static BlockResponse from(Block bloqueo) {
        return new BlockResponse(
                bloqueo.getId(),
                bloqueo.getStaffId(),
                bloqueo.getStartAt(),
                bloqueo.getEndAt(),
                bloqueo.getReason());
    }
}
