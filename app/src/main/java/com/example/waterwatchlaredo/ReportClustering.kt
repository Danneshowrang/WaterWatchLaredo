package com.example.waterwatchlaredo

import org.osmdroid.util.GeoPoint

private const val RECENT_REPORT_WINDOW_MILLIS = 24L * 60 * 60 * 1000
private const val MAX_DISTANCE_METERS = 500.0
private const val MIN_REPORTS_PER_AREA = 3

fun findPossibleProblemAreas(
    reports: List<Report>,
    nowMillis: Long,
    sampleDataOnly: Boolean = false
): List<List<Report>> {
    val oldestAllowedTime = nowMillis - RECENT_REPORT_WINDOW_MILLIS
    val recentReports = reports.filter { report ->
        // Demo mode selects ONLY samples; normal mode selects ONLY real reports.
        report.isSample == sampleDataOnly &&
            report.timestamp >= oldestAllowedTime && report.timestamp <= nowMillis &&
            report.latitude.isFinite() && report.latitude in -90.0..90.0 &&
            report.longitude.isFinite() && report.longitude in -180.0..180.0
    }.distinctBy { it.id }.sortedBy { it.id }

    val groups = mutableListOf<MutableList<Report>>()
    for (report in recentReports) {
        // Require proximity to EVERY member, so chains cannot stretch an area beyond 500 m.
        val matchingGroup = groups.firstOrNull { group ->
            // Every group starts with one report, which establishes its problem type.
            group.first().problemType == report.problemType && group.all { member ->
                GeoPoint(report.latitude, report.longitude).distanceToAsDouble(
                    GeoPoint(member.latitude, member.longitude)
                ) <= MAX_DISTANCE_METERS
            }
        }
        if (matchingGroup == null) {
            groups.add(mutableListOf(report))
        } else {
            matchingGroup.add(report)
        }
    }
    // Simple first-compatible-group assignment can miss overlapping clusters.
    return groups.filter { it.size >= MIN_REPORTS_PER_AREA }
}
