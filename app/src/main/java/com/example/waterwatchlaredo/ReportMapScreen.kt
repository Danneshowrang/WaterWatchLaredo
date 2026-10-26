package com.example.waterwatchlaredo

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import androidx.core.os.CancellationSignal
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import org.osmdroid.views.CustomZoomButtonsController
import androidx.compose.runtime.*
import androidx.compose.foundation.isSystemInDarkTheme
import com.example.waterwatchlaredo.ui.theme.SampleRose
import com.example.waterwatchlaredo.ui.theme.SampleRoseLight
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.BoundingBox
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.FolderOverlay
import org.osmdroid.views.overlay.Polyline
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ReportMapScreen(
    reportDao: ReportDao,
    lifecycle: Lifecycle,
    modifier: Modifier = Modifier,
    reportLatitude: Double? = null,
    reportLongitude: Double? = null,
    onReportFocused: () -> Unit = {},
    openSamplePreview: Boolean = false,
    onSamplePreviewOpened: () -> Unit = {}
) {
    val sampleColor = if (isSystemInDarkTheme()) SampleRoseLight else SampleRose
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Read again each time the user opens this screen.
    var reports by remember { mutableStateOf<List<Report>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf("") }
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var problemAreas by remember { mutableStateOf<List<List<Report>>>(emptyList()) }
    var checkingAreas by remember { mutableStateOf(true) }
    var showDemoAreas by rememberSaveable { mutableStateOf(false) }
    var selectedSignalArea by remember { mutableStateOf<List<Report>?>(null) }
    var signalNow by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showObservationHelp by remember { mutableStateOf(false) }
    var showAreaDetails by remember { mutableStateOf(false) }
    LaunchedEffect(selectedSignalArea) { showAreaDetails = false }
    var cityWorkNotices by remember { mutableStateOf<List<CityWorkNotice>>(emptyList()) }
    var cityWorkStatus by remember { mutableStateOf("") }

    LaunchedEffect(selectedSignalArea, showDemoAreas, lifecycle) {
        cityWorkNotices = emptyList()
        if (selectedSignalArea == null) return@LaunchedEffect
        if (showDemoAreas) {
            cityWorkStatus = "Real city notices are not matched to fictional sample reports."
            return@LaunchedEffect
        }
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                cityWorkNotices = emptyList()
                cityWorkStatus = "Checking city work context…"
                try {
                    cityWorkNotices = loadCityWorkNotices()
                    signalNow = System.currentTimeMillis()
                    cityWorkStatus = "No verified timing/proximity match for this area. " +
                        "This does not mean no city work or notices exist."
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    cityWorkStatus = "City work context unavailable; the report signal remains unconfirmed."
                }
                delay(60_000)
            }
        }
    }
    var waterMains by remember { mutableStateOf<WaterMainReference?>(null) }
    var loadingWaterMains by remember { mutableStateOf(false) }
    var waterMainStatus by remember { mutableStateOf("Water-main reference is off.") }
    val waterMainOverlay = remember { FolderOverlay() }
    var showMapTools by remember { mutableStateOf(false) }
    var locationStatus by remember { mutableStateOf("Location helps focus your nearby reports.") }
    var locating by remember { mutableStateOf(false) }
    var compactSummary by remember { mutableStateOf(true) }
    var triedAutomaticLocation by remember { mutableStateOf(false) }
    var locationRequest by remember { mutableStateOf<CancellationSignal?>(null) }
    var userLocation by remember { mutableStateOf<Location?>(null) }
    var waterMainsEnabled by rememberSaveable { mutableStateOf(false) }
    var mainRequestBounds by remember { mutableStateOf<BoundingBox?>(null) }
    val userLocationOverlay = remember { FolderOverlay() }

    fun focusPoints(points: List<GeoPoint>) {
        val map = mapView ?: return
        val bounds = reportFocusBounds(points) ?: return
        compactSummary = true
        // post waits for the View to finish layout before calculating the zoom.
        map.post {
            if (mapView === map && map.width > 0 && map.height > 0) {
                val padding = minOf((32 * context.resources.displayMetrics.density).toInt(),
                    map.width / 5, map.height / 5)
                map.zoomToBoundingBox(bounds, true, padding, 17.0, 350L)
            }
        }
        if (waterMainsEnabled) mainRequestBounds = bounds
    }

    fun locateAndFocus() {
        if (locating) return
        locating = true
        locationStatus = "Finding your location…"
        val request = CancellationSignal()
        locationRequest = request
        requestReportLocation(context, request) { location, error ->
            locating = false
            locationRequest = null
            if (location == null) {
                locationStatus = error ?: "Could not find your location. Try My area again."
            } else if (location.latitude !in 27.35..27.85 || location.longitude !in -99.65..-99.30) {
                locationStatus = "You are outside the Laredo map area. Showing Laredo instead."
            } else {
                userLocation = location
                val point = GeoPoint(location.latitude, location.longitude)
                // Include report groups within 3 km; do not pull the view to distant areas.
                val nearbyReports = problemAreas.filter { area ->
                    area.any { point.distanceToAsDouble(GeoPoint(it.latitude, it.longitude)) <= 3000.0 }
                }.flatten()
                focusPoints(listOf(point) + nearbyReports.map { GeoPoint(it.latitude, it.longitude) })
                locationStatus = if (nearbyReports.isEmpty()) "Showing your area. Location is not saved as a report."
                    else "Showing your location and nearby ${if (showDemoAreas) "DEMO" else "possible"} problem areas."
            }
        }
    }

    val mapPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            locateAndFocus()
        } else {
            locationStatus = "Location was not allowed. You can still explore Laredo. " +
                "Enable location in Android app settings if Android no longer asks."
        }
    }

    fun requestMapLocation() {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED) {
            locateAndFocus()
        } else {
            mapPermissionLauncher.launch(arrayOf(
                Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION
            ))
        }
    }

    LaunchedEffect(mapView, loading, openSamplePreview) {
        if (mapView != null && !loading && openSamplePreview) {
            triedAutomaticLocation = true
            showDemoAreas = true
            selectedSignalArea = null
            focusPoints(reports.filter { it.isSample }.map { GeoPoint(it.latitude, it.longitude) })
            compactSummary = true
            locationStatus = "Showing fictional sample reports in Laredo."
            onSamplePreviewOpened()
        }
    }

    LaunchedEffect(mapView, loading, checkingAreas, openSamplePreview) {
        if (mapView != null && !loading && !checkingAreas && !triedAutomaticLocation && !openSamplePreview) {
            triedAutomaticLocation = true
            if (reportLatitude != null && reportLongitude != null) {
                // An explicit report selection takes priority over the device location.
                focusPoints(listOf(GeoPoint(reportLatitude, reportLongitude)))
                locationStatus = "Showing your saved observation."
                onReportFocused()
            } else {
                requestMapLocation()
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose { locationRequest?.cancel() }
    }
    LaunchedEffect(mapView, userLocation) {
        userLocationOverlay.items.clear()
        val map = mapView
        userLocation?.let { location ->
            if (map != null) userLocationOverlay.add(Marker(map).apply {
                position = GeoPoint(location.latitude, location.longitude)
                title = "Your approximate location · not a water report"
                setTextIcon("YOU")
            })
        }
        map?.invalidate()
    }

    // Refresh reference geometry when the user enables it or selects a report area.
    LaunchedEffect(waterMainsEnabled, mainRequestBounds) {
        waterMains = null
        val bounds = mainRequestBounds
        if (!waterMainsEnabled || bounds == null) {
            waterMainStatus = "Water-main reference is off."
            return@LaunchedEffect
        }
        loadingWaterMains = true
        waterMainStatus = "Loading water-main reference…"
        try {
            val reference = loadWaterMainReference(bounds)
            waterMains = reference
            waterMainStatus = when {
                reference.isPartial -> "Partial reference: ${reference.mainCount} mains. Zoom in and reload."
                reference.mainCount == 0 -> "No lines returned. Missing data does not mean no mains exist."
                else -> "${reference.mainCount} mains loaded for the focused area."
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            waterMainStatus = "Water-main reference unavailable. Try reloading it in Map tools."
        } finally {
            loadingWaterMains = false
        }
    }


    LaunchedEffect(mapView, waterMains) {
        waterMainOverlay.items.clear()
        waterMains?.paths?.forEach { points ->
            val line = Polyline().apply {
                setPoints(points)
                outlinePaint.color = android.graphics.Color.rgb(0, 110, 170)
                outlinePaint.strokeWidth = 4f
                infoWindow = null
            }
            waterMainOverlay.add(line)
        }
        mapView?.invalidate()
    }

    // Recalculate off the UI thread, including when reports age out while the map stays open.
    LaunchedEffect(reports, lifecycle, showDemoAreas) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                checkingAreas = true
                signalNow = System.currentTimeMillis()
                problemAreas = withContext(Dispatchers.Default) {
                    findPossibleProblemAreas(reports, System.currentTimeMillis(), sampleDataOnly = showDemoAreas)
                }
                checkingAreas = false
                delay(60_000)
            }
        }
    }

    LaunchedEffect(reportDao) {
        try {
            reports = reportDao.getAll()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            errorMessage = "Could not load reports. Open the map again to retry."
        } finally {
            loading = false
        }
    }

    // Forward Android lifecycle events to osmdroid's traditional View.
    DisposableEffect(mapView, lifecycle) {
        val map = mapView
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> map?.onResume()
                Lifecycle.Event.ON_PAUSE -> map?.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) map?.onResume()
        onDispose {
            lifecycle.removeObserver(observer)
            map?.onPause()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (showDemoAreas) {
            Surface(color = MaterialTheme.colorScheme.primaryContainer) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("SAMPLE DATA · Fictional observations", modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelMedium)
                    TextButton(onClick = {
                        showDemoAreas = false
                        selectedSignalArea = null
                    }) { Text("Exit preview") }
                }
            }
        }
        // Keep the overview short; open the area list only when requested.
        if (selectedSignalArea == null) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("Reported water issues", style = MaterialTheme.typography.headlineSmall)
                val residentCount = reports.count { !it.isSample }
                Text("Laredo · $residentCount resident ${if (residentCount == 1) "report" else "reports"} · " +
                    "${reports.count { it.isSample }} samples · on this device",
                    style = MaterialTheme.typography.bodySmall)
                if (!compactSummary) {
                    Column(Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(if (showDemoAreas) "DEMO · possible problem areas" else "Possible problem areas",
                                    style = MaterialTheme.typography.titleMedium)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Preview sample clusters", modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodySmall)
                                    Switch(
                                        checked = showDemoAreas,
                                        onCheckedChange = {
                                            showDemoAreas = it
                                            selectedSignalArea = null
                                        },
                                        colors = SwitchDefaults.colors(
                                            uncheckedTrackColor = MaterialTheme.colorScheme.surface,
                                            uncheckedBorderColor = MaterialTheme.colorScheme.outline
                                        )
                                    )
                                }
                                Text(
                                    if (showDemoAreas) "Fictional samples only · last 24 hours · within 500 m"
                                    else "Last 24 hours · 3+ real reports · within 500 m",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                when {
                                    loading || checkingAreas -> Text("Checking recent reports…")
                                    errorMessage.isNotEmpty() -> Text("Problem areas unavailable.")
                                    problemAreas.isEmpty() -> Text(
                                        if (showDemoAreas) "No demo clusters. Load demo reports in Community."
                                        else "No recent clusters found. Water safety is not confirmed.",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    else -> Column {
                                        problemAreas.forEachIndexed { index, area ->
                                            TextButton(onClick = {
                                                selectedSignalArea = area
                                                signalNow = System.currentTimeMillis()
                                                waterMainsEnabled = true
                                                focusPoints(area.map { GeoPoint(it.latitude, it.longitude) })
                                                locationStatus = "Showing all ${area.size} ${if (showDemoAreas) "sample" else "resident"} reports in this area."
                                            }) {
                                                Text("${if (showDemoAreas) "DEMO area" else "Area"} ${index + 1}: ${area.size} reports — show on map")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { requestMapLocation() }, enabled = !locating) {
                Text(if (locating) "Locating…" else "My area")
            }
            TextButton(onClick = {
                if (selectedSignalArea != null) {
                    selectedSignalArea = null
                    compactSummary = false
                } else {
                    compactSummary = !compactSummary
                }
            }) {
                Text(when {
                    selectedSignalArea != null -> "All areas"
                    !compactSummary -> "Hide areas"
                    loading || checkingAreas -> "Checking areas…"
                    errorMessage.isNotEmpty() -> "Areas unavailable"
                    else -> "Areas (${problemAreas.size})"
                })
            }
            TextButton(onClick = { showMapTools = !showMapTools }) {
                Text(if (showMapTools) "Close tools" else "Map tools")
            }
        }
        selectedSignalArea?.let { area ->
            val signal = summarizeIssueSignal(area, signalNow, sampleDataOnly = showDemoAreas,
                cityNotices = cityWorkNotices)
            if (showObservationHelp) {
                AlertDialog(
                    onDismissRequest = { showObservationHelp = false },
                    title = { Text("What the observations may indicate") },
                    text = {
                        Column(
                            Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            signal.typeCounts.keys.forEach { problemType ->
                                guidanceForObservation(problemType)?.let { guidance ->
                                    Text(problemType, style = MaterialTheme.typography.titleSmall)
                                    Text(guidance.explanation, style = MaterialTheme.typography.bodyMedium)
                                    TextButton(onClick = { uriHandler.openUri(guidance.sourceUrl) }) {
                                        Text("Source: EPA ↗")
                                    }
                                }
                            }
                            Text("These are possible explanations, not findings about this location. " +
                                "Follow official city notices for current instructions.",
                                style = MaterialTheme.typography.bodySmall)
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showObservationHelp = false }) { Text("Got it") }
                    }
                )
            }

            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                    .heightIn(max = 220.dp)
            ) {
                Column(
                    Modifier.verticalScroll(rememberScrollState()).padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (signal.isDemo) "DEMO · nearby issue signal" else "Nearby issue signal",
                            modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                        TextButton(onClick = { selectedSignalArea = null }) { Text("Close") }
                    }
                    Text(signal.headline, style = MaterialTheme.typography.titleMedium)
                    Text("${signal.reportCount} ${if (signal.isDemo) "sample" else "resident"} reports · last 24 hours",
                        style = MaterialTheme.typography.bodySmall)
                    Text(signal.typeCounts.entries.joinToString(" · ") { "${it.key}: ${it.value}" },
                        style = MaterialTheme.typography.bodySmall)
                    signal.latestReportTime?.let { timestamp ->
                        val time = DateTimeFormatter.ofPattern("MMM d, h:mm a")
                            .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(timestamp))
                        Text("Latest report: $time", style = MaterialTheme.typography.bodySmall)
                    }
                    Text("✓ Highlighted pins belong to this selected area.",
                        style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { showAreaDetails = !showAreaDetails }) {
                        Text(if (showAreaDetails) "Less detail" else "Details & city context")
                    }
                    if (showAreaDetails) {
                        signal.cityWork?.let { notice ->
                            val dateFormat = DateTimeFormatter.ofPattern("MMM d, h:mm a").withZone(ZoneId.systemDefault())
                            Text("CITY CONTEXT · ${notice.workType}", style = MaterialTheme.typography.labelMedium)
                            Text("Listed OPEN · estimated window ${dateFormat.format(Instant.ofEpochMilli(notice.startsAt))} – " +
                                dateFormat.format(Instant.ofEpochMilli(notice.estimatedEndsAt!!)),
                                style = MaterialTheme.typography.bodySmall)
                            Text("The low-pressure reports are within 500 m of the work point and its estimated time window. " +
                                "This may explain them; the affected service area, shared cause, and water safety are unconfirmed.",
                                style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { uriHandler.openUri(OFFICIAL_OUTAGE_MAP) }) { Text("View city work source ↗") }
                        }
                        if (signal.cityWork == null && cityWorkStatus.isNotEmpty()) {
                            Text(cityWorkStatus, style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(
                            enabled = signal.typeCounts.isNotEmpty(),
                            onClick = { showObservationHelp = true }
                        ) { Text("What these observations may indicate") }
                        Text("Reports: latest 12h ${signal.latest12Hours} · previous 12h ${signal.previous12Hours}",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    Text("Unverified observations. Shared cause and spread are unconfirmed; this is not a water-safety forecast.",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Text(locationStatus, style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp))
        Text(
            "Unverified reports. This app does not replace official city notices.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        Box(
            Modifier.fillMaxWidth().weight(1f).padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(20.dp)).clipToBounds()
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    // Identify our app to the tile server and keep its cache inside the app.
                    Configuration.getInstance().apply {
                        // Map requests must identify our app, rather than a generic example package.
                        userAgentValue = "WaterWatchLaredo/1.0 (${context.packageName})"
                        osmdroidBasePath = File(context.cacheDir, "osmdroid")
                        osmdroidTileCache = File(osmdroidBasePath, "tiles")
                    }
                    MapView(context).apply {
                        // Reference lines stay below resident report pins.
                        overlays.add(waterMainOverlay)
                        overlays.add(userLocationOverlay)
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                        maxZoomLevel = 19.0
                        // Approximate Laredo-area viewing limits, not an official city boundary.
                        setScrollableAreaLimitDouble(BoundingBox(27.85, -99.30, 27.35, -99.65))
                        minZoomLevel = 12.0
                        controller.setZoom(12.0)
                        controller.setCenter(GeoPoint(27.5306, -99.4803)) // Laredo city view.
                        mapView = this
                    }
                },
                update = { map ->
                    // Rebuild pins when the reports or selected area changes, without moving the map.
                    val selectedIds = selectedSignalArea.orEmpty().map { it.id }.toSet()
                    val markerState = reports to selectedIds
                    if (map.tag != markerState) {
                        // Replace report pins so recomposition never duplicates them.
                        map.overlays.removeAll { it is Marker }
                        val dateFormat = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a")
                            .withZone(ZoneId.systemDefault())
                        // Draw highlighted pins last so nearby background pins cannot cover them.
                        reports.sortedBy { it.id in selectedIds }.forEach { report ->
                            val selected = report.id in selectedIds
                            val marker = Marker(map).apply {
                                position = GeoPoint(report.latitude, report.longitude)
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                title = if (report.isSample) "SAMPLE: ${report.problemType}" else report.problemType
                                icon = reportPinIcon(context, report.isSample, selected)
                                alpha = if (selectedIds.isEmpty() || selected) 1f else 0.35f
                                val time = dateFormat.format(Instant.ofEpochMilli(report.timestamp))
                                snippet = if (report.isSample) "Fictional sample data. $time" else time
                            }
                            map.overlays.add(marker)
                        }
                        // Keep the location label visible even when a report shares its coordinates.
                        map.overlays.remove(userLocationOverlay)
                        map.overlays.add(userLocationOverlay)
                        // Keep the initial Laredo view even if an old test report is elsewhere.
                        map.invalidate()
                        map.tag = markerState
                    }
                },
                onRelease = { map -> map.onDetach() }
            )
            Column(
                Modifier.align(Alignment.CenterEnd).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.End
            ) {
                FilledTonalIconButton(
                    onClick = { mapView?.controller?.zoomIn() },
                    modifier = Modifier.size(48.dp).semantics { contentDescription = "Zoom in" }
                ) { Text("+", style = MaterialTheme.typography.headlineSmall) }
                FilledTonalIconButton(
                    onClick = { mapView?.controller?.zoomOut() },
                    modifier = Modifier.size(48.dp).semantics { contentDescription = "Zoom out" }
                ) { Text("−", style = MaterialTheme.typography.headlineSmall) }
                FilledTonalButton(onClick = {
                    mapView?.controller?.setZoom(12.0)
                    mapView?.controller?.animateTo(GeoPoint(27.5306, -99.4803))
                }) { Text("Laredo") }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("● Sample", color = sampleColor,
                style = MaterialTheme.typography.labelMedium)
            Text("● Resident", color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelMedium)
            if (selectedSignalArea != null) {
                Text("✓ Selected", style = MaterialTheme.typography.labelMedium)
            }
        }
        if (waterMainsEnabled) {
            Text(
                "Blue lines: unofficial 2022 mains. Spatial context only; spread and flow direction are unknown. " +
                    waterMainStatus,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        if (showMapTools) {
            Column(
                Modifier.heightIn(max = 160.dp).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("Rose pins are fictional sample reports. Blue pins are resident observations. Tap a pin for its source and time.",
                    style = MaterialTheme.typography.bodySmall)
                Button(
                    enabled = !loading,
                    onClick = {
                        loading = true
                        errorMessage = ""
                        scope.launch {
                            try {
                                seedSampleReports(reportDao)
                                reports = reportDao.getAll()
                            } catch (error: CancellationException) {
                                throw error
                            } catch (error: Exception) {
                                errorMessage = "Could not load sample reports. Please try again."
                            } finally {
                                loading = false
                            }
                        }
                    }
                ) { Text("Add sample reports") }
                if (errorMessage.isNotEmpty()) Text(errorMessage)

                TextButton(onClick = {
                    uriHandler.openUri("https://laredoutilities.com/outages-map/")
                }) { Text("Official city outage map ↗") }
                TextButton(onClick = {
                    uriHandler.openUri("https://laredoutilities.com/wp-content/uploads/Laredo_IWMP.pdf")
                }) { Text("City water master plan (2022 PDF) ↗") }
                Text("Blue lines: unofficial water-main reference (Laredo Water 2022). " +
                    "May be outdated or incomplete. This app does not replace official city notices.")
                Row {
                    TextButton(
                        enabled = !loadingWaterMains && mapView != null,
                        onClick = {
                            waterMainsEnabled = true
                            mainRequestBounds = mapView?.boundingBox
                        }
                    ) { Text(if (loadingWaterMains) "Loading mains…" else "Load mains in this view") }
                    TextButton(
                        enabled = waterMains != null && !loadingWaterMains,
                        onClick = {
                            waterMainsEnabled = false
                            waterMains = null
                            waterMainStatus = "Water-main reference is off."
                        }
                    ) { Text("Hide") }
                }
                Text(waterMainStatus)
                Text(
                    "Source: Laredo Water 2022 · jstandford · ArcGIS",
                    modifier = Modifier.clickable {
                        uriHandler.openUri("https://www.arcgis.com/home/item.html?id=7682f59eac784bbe8e10618d1423afa0")
                    }.padding(vertical = 4.dp)
                )
            }
        }
        Text(
            "© OpenStreetMap contributors",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.clickable {
                uriHandler.openUri("https://www.openstreetmap.org/copyright")
            }.padding(8.dp)
        )
    }
}

// Padding prevents edge pins from being cut off and handles reports at the same point.
internal fun reportFocusBounds(points: List<GeoPoint>): BoundingBox? {
    val validPoints = points.filter {
        it.latitude.isFinite() && it.latitude in -90.0..90.0 &&
            it.longitude.isFinite() && it.longitude in -180.0..180.0
    }
    if (validPoints.isEmpty()) return null
    return BoundingBox(
        (validPoints.maxOf { it.latitude } + 0.001).coerceAtMost(90.0),
        (validPoints.maxOf { it.longitude } + 0.001).coerceAtMost(180.0),
        (validPoints.minOf { it.latitude } - 0.001).coerceAtLeast(-90.0),
        (validPoints.minOf { it.longitude } - 0.001).coerceAtLeast(-180.0)
    )
}

// Color identifies the source, not severity. A checkmark identifies selected reports.
private fun reportPinIcon(
    context: android.content.Context,
    isSample: Boolean,
    isSelected: Boolean
): android.graphics.drawable.Drawable {
    val density = context.resources.displayMetrics.density
    val width = (32 * density).toInt()
    val height = (42 * density).toInt()
    val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    // Draw in dp-sized coordinates so pins have the same apparent size on every device.
    canvas.scale(density, density)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    val pin = android.graphics.Path().apply {
        moveTo(16f, 40f)
        cubicTo(12f, 33f, 2f, 25f, 2f, 16f)
        cubicTo(2f, -2f, 30f, -2f, 30f, 16f)
        cubicTo(30f, 25f, 20f, 33f, 16f, 40f)
        close()
    }
    paint.color = android.graphics.Color.parseColor(if (isSample) "#A83870" else "#5965B3")
    canvas.drawPath(pin, paint)
    paint.color = android.graphics.Color.WHITE
    paint.style = android.graphics.Paint.Style.STROKE
    paint.strokeWidth = 2f
    canvas.drawPath(pin, paint)
    if (isSelected) {
        paint.strokeWidth = 3f
        paint.strokeCap = android.graphics.Paint.Cap.ROUND
        val check = android.graphics.Path().apply {
            moveTo(9f, 16f)
            lineTo(14f, 21f)
            lineTo(23f, 11f)
        }
        canvas.drawPath(check, paint)
    } else {
        paint.style = android.graphics.Paint.Style.FILL
        canvas.drawCircle(16f, 15f, 4f, paint)
    }
    return android.graphics.drawable.BitmapDrawable(context.resources, bitmap)
}
