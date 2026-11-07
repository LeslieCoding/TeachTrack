package com.openminis.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerVersionTest {
    @Test fun prereleaseBuildNumbersAreOrderedNumerically() {
        assertEquals(1, UpdateChecker.compareVersions("3.0.0-alpha.4", "3.0.0-alpha.3"))
        assertEquals(-1, UpdateChecker.compareVersions("3.0.0-alpha.2", "3.0.0-alpha.10"))
        assertEquals(0, UpdateChecker.compareVersions("v3.0.0-alpha.4", "3.0.0-alpha.4"))
    }

    @Test fun finalReleaseIsNewerThanPrereleaseAndNumericCoreWins() {
        assertTrue(UpdateChecker.compareVersions("3.0.0", "3.0.0-alpha.4") > 0)
        assertTrue(UpdateChecker.compareVersions("3.0.10", "3.0.9") > 0)
        assertTrue(UpdateChecker.compareVersions("3.0.0-alpha.4", "2.9.9") > 0)
    }
}
