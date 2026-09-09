package com.leslie.teachtrack

import com.leslie.teachtrack.core.model.MasteryLevel
import com.leslie.teachtrack.core.model.StudentScoreEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class ScoreRateTest {
    @Test fun scoreRateNormalizesDifferentFullScores() {
        assertEquals(0.7875, StudentScoreEntity("1", "s", "e", 126.0, 160.0).scoreRate, 0.00001)
    }

    @Test fun zeroFullScoreDoesNotProduceInfinity() {
        assertEquals(0.0, StudentScoreEntity("1", "s", "e", 10.0, 0.0).scoreRate, 0.0)
    }

    @Test fun masteryLevelsStayStableForStorage() {
        assertEquals(listOf(0, 1, 2, 3, 4), MasteryLevel.entries.map { it.value })
    }
}
