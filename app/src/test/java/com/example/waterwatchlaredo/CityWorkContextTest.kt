package com.example.waterwatchlaredo

import org.junit.Assert.*
import org.junit.Test

class CityWorkContextTest {
    private val now = 1_800_000_000_000L
    private val hour = 3_600_000L
    private fun reports() = (1L..3L).map { Report(id = it, problemType = "Low pressure",
        latitude = 27.53, longitude = -99.48, timestamp = now - 10_000) }
    private fun notice() = CityWorkNotice(7, "WATER MAIN REPAIR", true,
        now - hour, now + hour, 27.53, -99.48, now)

    @Test fun matchingCurrentWorkChangesTheSignalAndPreservesItsSourceContext() {
        val signal = summarizeIssueSignal(reports(), now, false, listOf(notice()))
        assertEquals("Low pressure may align with announced city work", signal.headline)
        assertEquals(7L, signal.cityWork?.id)
    }
    @Test fun unrelatedLocationCompletedWorkAndFutureWorkDoNotMatch() {
        val candidates = listOf(notice().copy(latitude = 27.60), notice().copy(isOpen = false),
            notice().copy(startsAt = now + hour, estimatedEndsAt = now + 2 * hour))
        assertNull(matchingCityWork(reports(), candidates, now))
    }
    @Test fun expiredUnknownAndStaleWindowsDoNotMatch() {
        val candidates = listOf(notice().copy(estimatedEndsAt = now - 1),
            notice().copy(estimatedEndsAt = null), notice().copy(checkedAt = now - 900_001))
        assertNull(matchingCityWork(reports(), candidates, now))
    }
    @Test fun reportsBeforeWorkCannotBeExplainedByTheWork() {
        assertNull(matchingCityWork(reports().map { it.copy(timestamp = now - 2 * hour) }, listOf(notice()), now))
    }
    @Test fun pressureNoticesMatchButOtherProblemTypesAndServiceConnectionsDoNot() {
        val pressureNotice = notice().copy(workType = "LOW WATER PRESSURE")
        assertEquals("Consistent with a nearby city pressure notice",
            summarizeIssueSignal(reports(), now, false, listOf(pressureNotice)).headline)
        assertNull(matchingCityWork(reports().map { it.copy(problemType = "Color") }, listOf(notice()), now))
        assertNull(matchingCityWork(reports(), listOf(notice().copy(workType = "SERVICE LINE REPAIR")), now))
    }
    @Test fun samplesAndInsufficientEvidenceCannotReceiveRealCityConfirmation() {
        val samples = reports().map { it.copy(isSample = true) }
        assertNull(summarizeIssueSignal(samples, now, true, listOf(notice())).cityWork)
        assertNull(matchingCityWork(reports().take(2), listOf(notice()), now))
    }
    @Test fun durationEstimatesUseTheUpperBoundAndUnknownDurationsStayUnknown() {
        assertEquals(8L, estimatedWorkHours("6-8 Hours"))
        assertNull(estimatedWorkHours("Until Further Notice"))
        assertNull(estimatedWorkHours("TBD"))
    }
}
