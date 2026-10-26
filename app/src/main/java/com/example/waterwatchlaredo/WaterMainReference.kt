package com.example.waterwatchlaredo

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

// Unofficial reference from the public "Laredo Water 2022" WATER MAIN layer only.
// No meters, services, laterals, or individual property connection layers are requested.
private const val WATER_MAIN_QUERY_URL =
    "https://services8.arcgis.com/CId9zss9qRBKDjB0/ArcGIS/rest/services/Laredo_Water/FeatureServer/13/query"

// This reference stays in memory; it is not stored with resident reports in Room.
data class WaterMainReference(
    val paths: List<List<GeoPoint>>,
    val mainCount: Int,
    val isPartial: Boolean
)

suspend fun loadWaterMainReference(bounds: BoundingBox): WaterMainReference = withContext(Dispatchers.IO) {
    // ArcGIS uses longitude,latitude order for an envelope.
    val envelope = "${bounds.lonWest},${bounds.latSouth},${bounds.lonEast},${bounds.latNorth}"
    val address = Uri.parse(WATER_MAIN_QUERY_URL).buildUpon()
        .appendQueryParameter("f", "json")
        .appendQueryParameter("where", "1=1")
        .appendQueryParameter("geometry", envelope)
        .appendQueryParameter("geometryType", "esriGeometryEnvelope")
        .appendQueryParameter("spatialRel", "esriSpatialRelIntersects")
        .appendQueryParameter("inSR", "4326")
        .appendQueryParameter("outSR", "4326")
        .appendQueryParameter("outFields", "OBJECTID")
        .appendQueryParameter("returnGeometry", "true")
        .appendQueryParameter("returnZ", "false")
        .appendQueryParameter("returnM", "false")
        .appendQueryParameter("resultRecordCount", "2000")
        .appendQueryParameter("orderByFields", "OBJECTID")
        .build()
    val connection = URL(address.toString()).openConnection() as HttpURLConnection
    connection.connectTimeout = 15_000
    connection.readTimeout = 15_000
    try {
        if (connection.responseCode != HttpURLConnection.HTTP_OK) {
            throw IOException("Water-main server is unavailable.")
        }
        val response = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val data = JSONObject(response)
        if (data.has("error")) throw IOException("Water-main server could not return the reference.")
        val features = data.getJSONArray("features")
        val paths = mutableListOf<List<GeoPoint>>()
        for (featureIndex in 0 until features.length()) {
            val geometry = features.getJSONObject(featureIndex).getJSONObject("geometry")
            val featurePaths = geometry.getJSONArray("paths")
            for (pathIndex in 0 until featurePaths.length()) {
                val coordinates = featurePaths.getJSONArray(pathIndex)
                val points = mutableListOf<GeoPoint>()
                for (pointIndex in 0 until coordinates.length()) {
                    val coordinate = coordinates.getJSONArray(pointIndex)
                    // ArcGIS returns [longitude, latitude]; GeoPoint expects the reverse.
                    val longitude = coordinate.getDouble(0)
                    val latitude = coordinate.getDouble(1)
                    require(latitude.isFinite() && latitude in -90.0..90.0 &&
                        longitude.isFinite() && longitude in -180.0..180.0)
                    points.add(GeoPoint(latitude, longitude))
                }
                if (points.size >= 2) paths.add(points)
            }
        }
        WaterMainReference(paths, features.length(), data.optBoolean("exceededTransferLimit", false))
    } finally {
        connection.disconnect()
    }
}
