package com.example.waterwatchlaredo

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.isSystemInDarkTheme
import com.example.waterwatchlaredo.ui.theme.SampleRose
import com.example.waterwatchlaredo.ui.theme.SampleRoseLight
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun CommunityScreen(reportDao: ReportDao, modifier: Modifier = Modifier) {
    val sampleColor = if (isSystemInDarkTheme()) SampleRoseLight else SampleRose
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    var reports by remember { mutableStateOf<List<Report>>(emptyList()) }
    var cityUpdates by remember { mutableStateOf<List<CityUpdate>>(emptyList()) }
    var loadingCity by remember { mutableStateOf(true) }
    var retryingCity by remember { mutableStateOf(false) }
    var showOfficialChannels by rememberSaveable { mutableStateOf(false) }
    var loadingReports by remember { mutableStateOf(true) }
    var selectedFilter by rememberSaveable { mutableStateOf("All") }
    val listState = rememberLazyListState()
    // A new filter starts at the heading instead of halfway down the previous list.
    LaunchedEffect(selectedFilter) { listState.scrollToItem(0) }
    val visibleReports = if (selectedFilter == "Residents") reports.filter { !it.isSample }
        else reports.sortedBy { it.isSample }
    var reportError by remember { mutableStateOf("") }
    var cityError by remember { mutableStateOf("") }
    var checkedAt by remember { mutableStateOf<Long?>(null) }
    var refreshKey by remember { mutableStateOf(0) }
    var cityRefreshKey by remember { mutableStateOf(0) }
    var addingSamples by remember { mutableStateOf(false) }
    val dateFormat = remember {
        DateTimeFormatter.ofPattern("MMM d, yyyy · h:mm a").withZone(ZoneId.systemDefault())
    }
    fun dateLabel(time: Long?) = if (time == null) "Date unavailable" else dateFormat.format(Instant.ofEpochMilli(time))

    // Local reports and city updates load independently; a network delay cannot block Room.
    LaunchedEffect(refreshKey, reportDao) {
        loadingReports = true
        reportError = ""
        try {
            reports = reportDao.getAll()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            reportError = "Could not load local reports. Tap Refresh to retry."
        } finally {
            loadingReports = false
        }
    }

    LaunchedEffect(cityRefreshKey) {
        loadingCity = true
        retryingCity = false
        cityError = ""
        try {
            cityUpdates = try {
                loadOfficialCityUpdates()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                // Retry once after a short pause; a second failure remains an unknown status.
                retryingCity = true
                delay(2_000)
                loadOfficialCityUpdates()
            }
            checkedAt = System.currentTimeMillis()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            cityError = if (cityUpdates.isEmpty()) {
                "City updates unavailable after retry. Notice status unknown; saved reports remain available."
            } else {
                "Refresh failed after retry. Showing older updates; current notice status unknown."
            }
        } finally {
            loadingCity = false
            retryingCity = false
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Community board", style = MaterialTheme.typography.headlineSmall)
            Text("This app does not replace official city notices.", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = !loadingReports && !addingSamples,
                    onClick = {
                        refreshKey++
                        if (!loadingCity) cityRefreshKey++
                    }) { Text(if (loadingReports) "Refreshing…" else "Refresh") }
                Button(enabled = !addingSamples && !loadingReports, onClick = {
                    addingSamples = true
                    scope.launch {
                        try {
                            seedSampleReports(reportDao)
                            reports = reportDao.getAll()
                            reportError = ""
                            selectedFilter = "All"
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            reportError = "Could not load demo reports. Please try again."
                        } finally {
                            addingSamples = false
                        }
                    }
                }) { Text(if (addingSamples) "Loading demo…" else "Load demo reports") }
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("All", "Residents", "City notices").forEach { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = filter },
                        label = { Text(filter) }
                    )
                }
            }
        }
        if (selectedFilter != "City notices") {
            item {
                Text("Resident reports", style = MaterialTheme.typography.titleLarge)
                Text("On this device · Unverified",
                    style = MaterialTheme.typography.bodySmall)
                if (reportError.isNotEmpty()) Text(reportError)
                if (loadingReports) Text("Loading saved observations…")
                if (!loadingReports && reportError.isEmpty() && visibleReports.isEmpty()) {
                    Text("No reports yet. Add one in the Report tab.")
                }
            }
            items(visibleReports, key = { "resident:${it.id}" }) { report ->
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, if (report.isSample) sampleColor else MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (report.isSample) "SAMPLE · fictional" else "RESIDENT · unverified",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (report.isSample) sampleColor else MaterialTheme.colorScheme.primary)
                        Text(report.problemType, style = MaterialTheme.typography.titleMedium)
                        Text(dateLabel(report.timestamp), style = MaterialTheme.typography.bodySmall)
                        if (report.note.isNotBlank()) Text(report.note, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        if (selectedFilter != "Residents") {
            item {
                Text("City updates", style = MaterialTheme.typography.titleLarge)
                Text("Utilities feed · Check each source for current status.",
                    style = MaterialTheme.typography.bodySmall)
                if (loadingCity) Text(
                    if (retryingCity) "Retrying city updates…"
                    else "Checking city updates…"
                )
                if (cityError.isNotEmpty()) {
                    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(12.dp)) {
                        Column(Modifier.fillMaxWidth().padding(12.dp)) {
                            Text(cityError, color = MaterialTheme.colorScheme.onErrorContainer)
                            TextButton(enabled = !loadingCity, onClick = { cityRefreshKey++ }) {
                                Text("Retry city updates")
                            }
                        }
                    }
                }
                if (!loadingCity && cityError.isEmpty() && cityUpdates.isEmpty()) Text(
                    "No updates in this feed. Other city channels may have notices.")
                TextButton(onClick = { showOfficialChannels = !showOfficialChannels }) {
                    Text(if (showOfficialChannels) "Hide other official channels" else "Other official channels")
                }
                if (showOfficialChannels) {
                    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(12.dp)) {
                        Column(Modifier.fillMaxWidth().padding(12.dp)) {
                            Text("This board imports the Utilities feed only. These additional channels open externally.",
                                style = MaterialTheme.typography.bodySmall)
                            if (checkedAt != null) Text("Last checked: ${dateLabel(checkedAt)}",
                                style = MaterialTheme.typography.labelSmall)
                            // Public links verified from the Utilities website and city newsroom.
                            val channels = listOf(
                                "Utilities updates" to "https://laredoutilities.com/utilities-updates/",
                                "Utilities public notices" to "https://laredoutilities.com/resources/public-notices/",
                                "Official outage map" to OFFICIAL_OUTAGE_MAP,
                                "City newsroom" to "https://www.cityoflaredo.com/government/newsroom",
                                "Utilities Facebook" to "https://www.facebook.com/cityoflaredoutilities",
                                "Utilities Instagram" to "https://www.instagram.com/laredo_utilities/"
                            )
                            channels.forEach { (label, url) ->
                                TextButton(onClick = { uriHandler.openUri(url) }) { Text("$label ↗") }
                            }
                        }
                    }
                }
            }
            items(cityUpdates, key = { "city:${it.sourceUrl}" }) { update ->
                // Amber distinguishes provenance, not severity or active status.
                Surface(
                    color = Color(0xFFFFF1D6), contentColor = Color(0xFF563800),
                    border = BorderStroke(1.dp, Color(0xFFC68B24)), shape = RoundedCornerShape(16.dp)
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("CITY SOURCE · Laredo Utilities", style = MaterialTheme.typography.labelMedium)
                        Text(update.title, style = MaterialTheme.typography.titleMedium)
                        Text("Published: ${dateLabel(update.publishedAt)}", style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { uriHandler.openUri(update.sourceUrl) }) { Text("Read city update ↗") }
                    }
                }
            }
        }
    }
}
