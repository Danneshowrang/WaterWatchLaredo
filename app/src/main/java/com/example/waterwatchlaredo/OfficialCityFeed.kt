package com.example.waterwatchlaredo

import android.net.Uri
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

// City-published source, separate from resident reports and fictional demo data.
const val OFFICIAL_UTILITIES_FEED = "https://laredoutilities.com/feed/"

data class CityUpdate(val title: String, val publishedAt: Long?, val sourceUrl: String)

suspend fun loadOfficialCityUpdates(): List<CityUpdate> = withContext(Dispatchers.IO) {
    val connection = URL(OFFICIAL_UTILITIES_FEED).openConnection() as HttpURLConnection
    connection.connectTimeout = 15_000
    connection.readTimeout = 15_000
    connection.instanceFollowRedirects = false
    try {
        if (connection.responseCode != HttpURLConnection.HTTP_OK) throw IOException("City feed unavailable")
        val xml = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        // This public RSS feed needs no external document definitions.
        if (xml.contains("<!DOCTYPE", ignoreCase = true)) throw IOException("Unexpected feed format")
        val parser = Xml.newPullParser()
        parser.setInput(xml.reader())
        val updates = mutableListOf<CityUpdate>()
        var insideItem = false
        var title = ""
        var link = ""
        var date = ""
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "item" -> { insideItem = true; title = ""; link = ""; date = "" }
                    "title" -> if (insideItem) title = parser.nextText().trim()
                    "link" -> if (insideItem) link = parser.nextText().trim()
                    "pubDate" -> if (insideItem) date = parser.nextText().trim()
                }
            } else if (parser.eventType == XmlPullParser.END_TAG && parser.name == "item") {
                val uri = Uri.parse(link)
                // Only city-utility HTTPS links can be labeled as city-published.
                if (title.isNotBlank() && uri.scheme == "https" &&
                    uri.host in listOf("laredoutilities.com", "www.laredoutilities.com")) {
                    val published = try {
                        ZonedDateTime.parse(date, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
                    } catch (error: java.time.format.DateTimeParseException) {
                        null
                    }
                    updates.add(CityUpdate(title, published, link))
                }
                insideItem = false
            }
            parser.next()
        }
        updates.distinctBy { it.sourceUrl }
    } finally {
        connection.disconnect()
    }
}
