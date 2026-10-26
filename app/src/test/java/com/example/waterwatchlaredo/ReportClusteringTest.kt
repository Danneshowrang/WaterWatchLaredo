package com.example.waterwatchlaredo

import org.junit.Assert.*
import org.junit.Test
import org.osmdroid.util.GeoPoint

class ReportClusteringTest {
    private val now = 1_800_000_000_000L
    private val day = 86_400_000L

    private fun report(id: Long, latitude: Double = 27.53, longitude: Double = -99.48) =
        Report(id = id, problemType = "Taste", latitude = latitude,
            longitude = longitude, timestamp = now)

    @Test fun threeNearbyReportsFormAnAreaButTwoDoNot() {
        val reports = listOf(report(1), report(2, 27.531), report(3, 27.532))
        assertEquals(1, findPossibleProblemAreas(reports, now).size)
        assertTrue(findPossibleProblemAreas(reports.take(2), now).isEmpty())
    }

    @Test fun samplesOldReportsAndFutureReportsDoNotCount() {
        val reports = listOf(report(1), report(2), report(3).copy(isSample = true),
            report(4).copy(timestamp = now - day - 1), report(5).copy(timestamp = now + 1))
        assertTrue(findPossibleProblemAreas(reports, now).isEmpty())
    }

    @Test fun reportExactly24HoursOldCountsThenExpires() {
        val reports = listOf(report(1), report(2), report(3).copy(timestamp = now - day))
        assertEquals(1, findPossibleProblemAreas(reports, now).size)
        assertTrue(findPossibleProblemAreas(reports, now + 1).isEmpty())
    }

    @Test fun nearbyChainCannotJoinDistantEndpoints() {
        // Adjacent points are about 334 m apart; endpoints are about 668 m apart.
        val reports = listOf(report(1, 27.530), report(2, 27.533), report(3, 27.536))
        assertTrue(findPossibleProblemAreas(reports, now).isEmpty())
    }

    @Test fun distanceLimitAccepts499MetersButRejects501Meters() {
        val origin = GeoPoint(27.53, -99.48)
        val inside = origin.destinationPoint(499.0, 0.0)
        val outside = origin.destinationPoint(501.0, 0.0)
        val base = listOf(report(1), report(2))
        assertEquals(1, findPossibleProblemAreas(base +
            report(3, inside.latitude, inside.longitude), now).size)
        assertTrue(findPossibleProblemAreas(base +
            report(3, outside.latitude, outside.longitude), now).isEmpty())
    }

    @Test fun separatesTwoDistantAreas() {
        val reports = listOf(report(1), report(2), report(3),
            report(4, 27.60), report(5, 27.60), report(6, 27.60))
        assertEquals(listOf(3, 3), findPossibleProblemAreas(reports, now).map { it.size })
    }

    @Test fun duplicateIdsAndInvalidCoordinatesDoNotInflateCounts() {
        val reports = listOf(report(1), report(1), report(2),
            report(3, Double.NaN), report(4, 91.0), report(5, longitude = 181.0))
        assertTrue(findPossibleProblemAreas(reports, now).isEmpty())
    }

    @Test fun demoModeKeepsSamplesAndRealReportsSeparate() {
        val residents = listOf(report(1), report(2), report(3))
        val samples = listOf(report(-1), report(-2), report(-3)).map { it.copy(isSample = true) }
        val allReports = residents + samples
        val realArea = findPossibleProblemAreas(allReports, now).single()
        val demoArea = findPossibleProblemAreas(allReports, now, sampleDataOnly = true).single()
        assertEquals(residents, realArea)
        assertEquals(setOf(-1L, -2L, -3L), demoArea.map { it.id }.toSet())
        assertTrue(demoArea.all { it.isSample })
        assertTrue(findPossibleProblemAreas(samples, now).isEmpty())
        assertTrue(findPossibleProblemAreas(residents, now, sampleDataOnly = true).isEmpty())
    }

    @Test fun changingInputOrderKeepsTheSameGrouping() {
        val reports = listOf(report(1), report(2, 27.531), report(3, 27.532))
        assertEquals(findPossibleProblemAreas(reports, now),
            findPossibleProblemAreas(reports.reversed(), now))
    }

    @Test fun threeSameTypeReportsWithinDistanceAndTimeFormOneArea() {
        val reports = listOf(
            report(1).copy(timestamp = now - 2 * 3_600_000L),
            report(2, 27.531).copy(timestamp = now - 3_600_000L),
            report(3, 27.532)
        )
        val areas = findPossibleProblemAreas(reports, now)
        assertEquals(1, areas.size)
        assertEquals(reports, areas.single())
    }

    @Test fun twoTasteReportsAndOneSmellReportDoNotFormAnArea() {
        val reports = listOf(
            report(1), report(2, 27.531),
            report(3, 27.532).copy(problemType = "Smell")
        )
        assertTrue(findPossibleProblemAreas(reports, now).isEmpty())
    }

    @Test fun threeTasteReportsExcludeNearbySmellReport() {
        val tastes = listOf(report(1), report(2, 27.531), report(3, 27.532))
        val smell = report(4, 27.5315).copy(problemType = "Smell")
        val areas = findPossibleProblemAreas(tastes + smell, now)
        assertEquals(1, areas.size)
        assertEquals(tastes, areas.single())
    }

    @Test fun interleavedTypeIdsStillProduceExactlyThreeTasteReports() {
        val firstTaste = report(1)
        val smell = report(2, 27.531).copy(problemType = "Smell")
        val secondTaste = report(3, 27.5315)
        val thirdTaste = report(4, 27.532)
        // Input is shuffled; sorting IDs yields Taste, Smell, Taste, Taste.
        val reports = listOf(thirdTaste, smell, firstTaste, secondTaste)
        val areas = findPossibleProblemAreas(reports, now)
        assertEquals(1, areas.size)
        assertEquals(listOf(firstTaste, secondTaste, thirdTaste), areas.single())
    }
}
