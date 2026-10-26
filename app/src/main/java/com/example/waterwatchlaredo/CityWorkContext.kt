package com.example.waterwatchlaredo

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

const val OFFICIAL_OUTAGE_MAP = "https://laredoutilities.com/outages-map/"
private const val CITY_WORK_LAYER =
    "https://services6.arcgis.com/8FCipc4uuUgyEpbK/arcgis/rest/services/Water_Service_Disruption_COPY_view/FeatureServer/0/query"

// City work locations are reference points, not confirmed service-area boundaries.
data class CityWorkNotice(
    val id: Long,
    val workType: String,
    val isOpen: Boolean,
    val startsAt: Long,
    val estimatedEndsAt: Long?,
    val latitude: Double,
    val longitude: Double,
    val checkedAt: Long
)

private val relevantCityWork = setOf(
    "WATER MAIN REPAIR", "PLANNED OUTAGE", "PLANNED MAINTENANCE", "LOW WATER PRESSURE"
)

// Use the upper end of the city's ESTIMATE; unknown durations cannot justify a time match.
internal fun estimatedWorkHours(duration: String): Long? = when (duration) {
    "2-4 Hours" -> 4L
    "4-6 Hours" -> 6L
    "6-8 Hours" -> 8L
    "8-12 Hours" -> 12L
    "24 Hours" -> 24L
    else -> null
}

suspend fun loadCityWorkNotices(): List<CityWorkNotice> = withContext(Dispatchers.IO) {
    val query = Uri.parse(CITY_WORK_LAYER).buildUpon()
        .appendQueryParameter("f", "json")
        // Never request individual property service-line repair records or address fields.
        .appendQueryParameter("where", "Status_1 = 'OPEN' AND Descriptio IN " +
            "('WATER MAIN REPAIR','PLANNED OUTAGE','PLANNED MAINTENANCE','LOW WATER PRESSURE')")
        .appendQueryParameter("outFields", "FID,Descriptio,Status_1,Actual_Sta,Estimate_Duration")
        .appendQueryParameter("returnGeometry", "true")
        .appendQueryParameter("outSR", "4326")
        .appendQueryParameter("resultRecordCount", "500")
        .build()
    val connection = URL(query.toString()).openConnection() as HttpURLConnection
    connection.connectTimeout = 15_000
    connection.readTimeout = 15_000
    try {
        if (connection.responseCode != HttpURLConnection.HTTP_OK) throw IOException("City work source unavailable")
        val response = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val data = JSONObject(response)
        if (data.has("error") || data.optBoolean("exceededTransferLimit", false)) {
            throw IOException("City work source did not return a complete response")
        }
        val features = data.getJSONArray("features")
        val notices = mutableListOf<CityWorkNotice>()
        val checkedAt = System.currentTimeMillis()
        for (index in 0 until features.length()) {
            val feature = features.getJSONObject(index)
            val attributes = feature.getJSONObject("attributes")
            val workType = attributes.optString("Descriptio")
            if (workType !in relevantCityWork || attributes.isNull("Actual_Sta")) continue
            val geometry = feature.optJSONObject("geometry") ?: continue
            val latitude = geometry.optDouble("y", Double.NaN)
            val longitude = geometry.optDouble("x", Double.NaN)
            if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) continue
            val start = attributes.getLong("Actual_Sta")
            val hours = estimatedWorkHours(attributes.optString("Estimate_Duration"))
            notices.add(CityWorkNotice(
                id = attributes.getLong("FID"), workType = workType,
                isOpen = attributes.optString("Status_1") == "OPEN",
                startsAt = start, estimatedEndsAt = hours?.let { start + it * 3_600_000L },
                latitude = latitude, longitude = longitude, checkedAt = checkedAt
            ))
        }
        notices
    } finally {
        connection.disconnect()
    }
}

// A proximity/time comparison supplies context, never proof that a city event caused a report.
fun matchingCityWork(reports: List<Report>, notices: List<CityWorkNotice>, nowMillis: Long): CityWorkNotice? {
    val pressureReports = reports.filter { !it.isSample && it.problemType == "Low pressure" &&
        it.timestamp in (nowMillis - 86_400_000L)..nowMillis }.distinctBy { it.id }
    if (pressureReports.size < 3) return null
    return notices.sortedByDescending { it.startsAt }.firstOrNull { notice ->
        val end = notice.estimatedEndsAt
        notice.workType in relevantCityWork && notice.isOpen && end != null && end > notice.startsAt &&
            nowMillis - notice.checkedAt in 0L..900_000L && // Discard context older than 15 minutes.
            nowMillis >= notice.startsAt && nowMillis <= end &&
            notice.latitude in -90.0..90.0 && notice.longitude in -180.0..180.0 &&
            pressureReports.all { report ->
                report.timestamp in notice.startsAt..end &&
                    report.latitude in -90.0..90.0 && report.longitude in -180.0..180.0 &&
                    GeoPoint(report.latitude, report.longitude).distanceToAsDouble(
                        GeoPoint(notice.latitude, notice.longitude)
                    ) <= 500.0
            }
    }
}
