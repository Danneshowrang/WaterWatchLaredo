package com.example.waterwatchlaredo

// SAMPLE DATA ONLY: fictional observations at illustrative locations in Laredo.
suspend fun seedSampleReports(reportDao: ReportDao) {
    val sampleTimestamp = System.currentTimeMillis()
    // Negative IDs are reserved for samples; real reports get positive IDs from Room.
    val sampleReports = listOf(
        Report(
            id = -1,
            problemType = "Taste",
            note = "SAMPLE DATA: fictional taste report, not a real observation.",
            latitude = 27.5306,
            longitude = -99.4803,
            timestamp = sampleTimestamp,
            isSample = true
        ),
        Report(
            id = -2,
            problemType = "Color",
            note = "SAMPLE DATA: fictional color report, not a real observation.",
            latitude = 27.5400,
            longitude = -99.4900,
            timestamp = sampleTimestamp,
            isSample = true
        ),
        Report(
            id = -3,
            problemType = "Low pressure",
            note = "SAMPLE DATA: fictional pressure report, not a real observation.",
            latitude = 27.5200,
            longitude = -99.5000,
            timestamp = sampleTimestamp,
            isSample = true
        ),
        // Two separate clusters plus one isolated report make the demo easy to explain.
        Report(id = -4, problemType = "Taste", note = "SAMPLE DATA: unusual taste near a public park.",
            latitude = 27.5720, longitude = -99.4910, timestamp = sampleTimestamp, isSample = true),
        Report(id = -5, problemType = "Taste", note = "SAMPLE DATA: fictional taste observation in the same area.",
            latitude = 27.5730, longitude = -99.4900, timestamp = sampleTimestamp, isSample = true),
        Report(id = -6, problemType = "Taste", note = "SAMPLE DATA: fictional taste observation nearby.",
            latitude = 27.5710, longitude = -99.4920, timestamp = sampleTimestamp, isSample = true),
        Report(id = -7, problemType = "Low pressure", note = "SAMPLE DATA: fictional low pressure in south Laredo.",
            latitude = 27.4900, longitude = -99.4760, timestamp = sampleTimestamp, isSample = true),
        Report(id = -8, problemType = "Low pressure", note = "SAMPLE DATA: another fictional pressure observation.",
            latitude = 27.4910, longitude = -99.4750, timestamp = sampleTimestamp, isSample = true),
        Report(id = -9, problemType = "Low pressure", note = "SAMPLE DATA: fictional pressure observation nearby.",
            latitude = 27.4890, longitude = -99.4770, timestamp = sampleTimestamp, isSample = true),
        Report(id = -10, problemType = "Smell", note = "SAMPLE DATA: an isolated fictional observation.",
            latitude = 27.6000, longitude = -99.4700, timestamp = sampleTimestamp, isSample = true)
    )
    reportDao.insertSampleReports(sampleReports)
}
