package com.leslie.teachtrack.practice;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MasteryModels() {
    public record MasteryRow(
            UUID knowledgePointId,
            double masteryScore,
            double confidence,
            int practiceCount,
            int correctCount,
            int wrongCount,
            String trend
    ) {
    }

    public record Evidence(boolean correct, int difficulty, Instant occurredAt) {
    }
}
