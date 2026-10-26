package com.example.waterwatchlaredo

import org.junit.Assert.*
import org.junit.Test

class NearbyIssueSignalTest {
    private val now = 1_800_000_000_000L
    private val hour = 3_600_000L
    private fun report(id: Long, type: String = "Low pressure", time: Long = now, sample: Boolean = false) =
        Report(id = id, problemType = type, latitude = 27.53, longitude = -99.48,
            timestamp = time, isSample = sample)

    @Test fun sparseEvidenceStaysInsufficient() {
        val signal = summarizeIssueSignal(listOf(report(1), report(2)), now, false)
        assertEquals("Insufficient reports", signal.headline)
        assertEquals(2, signal.reportCount)
    }
    @Test fun threePressureReportsHaveAnUnconfirmedServiceSignal() {
        val signal = summarizeIssueSignal(listOf(report(1), report(2), report(3)), now, false)
        assertEquals("Possible local service issue", signal.headline)
        assertEquals(3, signal.typeCounts["Low pressure"])
    }
    @Test fun mixedTypesAreCountedWithoutClaimingASharedCause() {
        val signal = summarizeIssueSignal(listOf(report(1), report(2, "Taste"), report(3, "Smell")), now, false)
        assertEquals("Nearby reports may be related", signal.headline)
        assertEquals(3, signal.typeCounts.size)
    }
    @Test fun samplesFutureOldAndDuplicateReportsCannotInflateResidentEvidence() {
        val reports = listOf(report(1), report(1), report(2, sample = true),
            report(3, time = now + 1), report(4, time = now - 24 * hour - 1))
        assertEquals(1, summarizeIssueSignal(reports, now, false).reportCount)
        val demo = summarizeIssueSignal(reports, now, true)
        assertEquals(1, demo.reportCount)
        assertTrue(demo.isDemo)
    }
    @Test fun twelveHourBoundaryIsCountedOnceAndLatestTimeIsPreserved() {
        val signal = summarizeIssueSignal(listOf(report(1), report(2, time = now - 12 * hour),
            report(3, time = now - 24 * hour)), now, false)
        assertEquals(2, signal.latest12Hours)
        assertEquals(1, signal.previous12Hours)
        assertEquals(now, signal.latestReportTime)
    }
}
