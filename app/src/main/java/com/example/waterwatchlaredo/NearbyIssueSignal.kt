package com.example.waterwatchlaredo

// A description of observations in an already selected cluster, not a water-quality prediction.
data class NearbyIssueSignal(
    val headline: String,
    val reportCount: Int,
    val typeCounts: Map<String, Int>,
    val latestReportTime: Long?,
    val latest12Hours: Int,
    val previous12Hours: Int,
    val isDemo: Boolean,
    val cityWork: CityWorkNotice? = null
)

fun summarizeIssueSignal(
    area: List<Report>,
    nowMillis: Long,
    sampleDataOnly: Boolean,
    cityNotices: List<CityWorkNotice> = emptyList()
): NearbyIssueSignal {
    val twelveHours = 12L * 60 * 60 * 1000
    val reports = area.filter {
        it.isSample == sampleDataOnly && it.timestamp >= nowMillis - 2 * twelveHours &&
            it.timestamp <= nowMillis
    }.distinctBy { it.id }
    val typeCounts = reports.groupingBy { it.problemType }.eachCount().toSortedMap()
    val latestCount = reports.count { it.timestamp >= nowMillis - twelveHours }
    val cityWork = if (sampleDataOnly) null else matchingCityWork(reports, cityNotices, nowMillis)
    val headline = when {
        reports.size < 3 -> "Insufficient reports"
        cityWork?.workType == "LOW WATER PRESSURE" -> "Consistent with a nearby city pressure notice"
        cityWork != null -> "Low pressure may align with announced city work"
        (typeCounts["Low pressure"] ?: 0) >= 3 -> "Possible local service issue"
        else -> "Nearby reports may be related"
    }
    return NearbyIssueSignal(
        headline = headline,
        reportCount = reports.size,
        typeCounts = typeCounts,
        latestReportTime = reports.maxOfOrNull { it.timestamp },
        latest12Hours = latestCount,
        previous12Hours = reports.size - latestCount,
        isDemo = sampleDataOnly,
        cityWork = cityWork
    )
}

// General education from EPA guidance, never a diagnosis of a submitted report.
data class ObservationGuidance(val explanation: String, val sourceUrl: String)

fun guidanceForObservation(problemType: String): ObservationGuidance? {
    val aestheticSource = "https://www.epa.gov/sdwa/secondary-drinking-water-standards-guidance-nuisance-chemicals"
    return when (problemType) {
        "Low pressure" -> ObservationGuidance(
            "Low pressure may reflect a leak, maintenance, or changing demand. " +
                "Pressure loss can allow contaminants through existing openings, but this report confirms neither a leak nor contamination.",
            "https://www.epa.gov/system/files/documents/2021-12/ds-toolbox-fact-sheets_pressure-management.pdf"
        )
        "Taste" -> ObservationGuidance(
            "An unusual taste may reflect dissolved minerals or treatment effects; taste alone cannot establish whether water is safe.",
            aestheticSource
        )
        "Smell" -> ObservationGuidance(
            "A smell change may reflect dissolved substances or treatment effects; odor alone cannot identify contamination or determine water safety.",
            aestheticSource
        )
        "Color" -> ObservationGuidance(
            "A color change may reflect dissolved organic material or metals such as iron; appearance alone cannot identify the cause or water safety.",
            aestheticSource
        )
        else -> null
    }
}
