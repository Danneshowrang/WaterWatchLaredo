package com.example.waterwatchlaredo

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import androidx.core.os.CancellationSignal
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.example.waterwatchlaredo.ui.theme.WaterWatchLaredoTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

// Maximum accepted horizontal accuracy for attaching a report location.
private const val LIMIT_METERS = 100f

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val reportDao = WaterWatchDatabase.getInstance(applicationContext).reportDao()
        setContent {
            WaterWatchLaredoTheme {
                var selectedTab by rememberSaveable { mutableStateOf("Map") }
                val tabState = rememberSaveableStateHolder()
                val welcomeScope = rememberCoroutineScope()
                var loadingDemo by remember { mutableStateOf(false) }
                var demoError by remember { mutableStateOf("") }
                var openSamplePreview by rememberSaveable { mutableStateOf(false) }
                var reportLatitude by rememberSaveable { mutableStateOf<Double?>(null) }
                var reportLongitude by rememberSaveable { mutableStateOf<Double?>(null) }
                val welcomePreferences = remember {
                    applicationContext.getSharedPreferences("waterwatch_welcome", Context.MODE_PRIVATE)
                }
                var showWelcome by rememberSaveable {
                    mutableStateOf(!welcomePreferences.getBoolean("welcome_seen", false))
                }
                if (showWelcome) {
                    WelcomeScreen(onExplore = {
                        openSamplePreview = false
                        welcomePreferences.edit().putBoolean("welcome_seen", true).apply()
                        selectedTab = "Map"
                        showWelcome = false
                    }, onExploreDemo = {
                        loadingDemo = true
                        demoError = ""
                        welcomeScope.launch {
                            try {
                                // Reuse the labeled sample IDs; repeated demos do not add duplicates.
                                seedSampleReports(reportDao)
                                reportLatitude = null
                                reportLongitude = null
                                openSamplePreview = true
                                selectedTab = "Map"
                                welcomePreferences.edit().putBoolean("welcome_seen", true).apply()
                                showWelcome = false
                            } catch (error: CancellationException) {
                                throw error
                            } catch (error: Exception) {
                                demoError = "Could not prepare sample reports. Tap Explore demo to retry."
                            } finally {
                                loadingDemo = false
                            }
                        }
                    }, loadingDemo = loadingDemo, demoError = demoError)
                    return@WaterWatchLaredoTheme
                }
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                            listOf("Map", "Report", "Community").forEach { tab ->
                                NavigationBarItem(
                                    selected = selectedTab == tab,
                                    onClick = { selectedTab = tab },
                                    icon = {
                                        Text(when (tab) {
                                            "Map" -> "◎"
                                            "Report" -> "+"
                                            else -> "≡"
                                        }, style = MaterialTheme.typography.titleLarge)
                                    },
                                    label = { Text(tab) }
                                )
                            }
                        }
                    }
                ) { padding ->
                    Column(Modifier.fillMaxSize().padding(padding)) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("WATERWATCH / LAREDO", style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                            if (selectedTab == "Report") {
                                TextButton(onClick = { showWelcome = true }) { Text("About") }
                            }
                            if (selectedTab != "Report") {
                                Button(onClick = { selectedTab = "Report" }) { Text("Report a problem") }
                            }
                        }
                        // Preserve text drafts when switching bottom tabs.
                        tabState.SaveableStateProvider(selectedTab) {
                            when (selectedTab) {
                                "Map" -> ReportMapScreen(
                                    reportDao, lifecycle, Modifier.weight(1f),
                                    reportLatitude, reportLongitude,
                                    onReportFocused = { reportLatitude = null; reportLongitude = null },
                                    openSamplePreview = openSamplePreview,
                                    onSamplePreviewOpened = { openSamplePreview = false }
                                )
                                "Report" -> AccountReportScreen(
                                    reportDao, Modifier.weight(1f),
                                    onViewMap = { latitude, longitude ->
                                        reportLatitude = latitude
                                        reportLongitude = longitude
                                        selectedTab = "Map"
                                    },
                                    onViewCommunity = { selectedTab = "Community" }
                                )
                                "Community" -> CommunityScreen(reportDao, Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ReportForm(
    reportDao: ReportDao,
    modifier: Modifier = Modifier,
    onViewMap: (Double, Double) -> Unit = { _, _ -> },
    onViewCommunity: () -> Unit = {}
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val problemTypes = listOf("Taste", "Smell", "Color", "Low pressure")
    var problemType by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var location by remember { mutableStateOf<Location?>(null) }
    var locating by remember { mutableStateOf(false) }
    var requestingPermission by rememberSaveable { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var locationRequest by remember { mutableStateOf<CancellationSignal?>(null) }
    // Keep the confirmation across tab changes without saving another report.
    var savedLatitude by rememberSaveable { mutableStateOf<Double?>(null) }
    var savedLongitude by rememberSaveable { mutableStateOf<Double?>(null) }
    var savedProblemType by rememberSaveable { mutableStateOf("") }

    fun captureLocation() {
        location = null
        locating = true
        message = ""
        val request = CancellationSignal()
        locationRequest = request
        requestReportLocation(context, request) { result, error ->
            locating = false
            locationRequest = null
            val accurateEnough = result != null && result.hasAccuracy() &&
                result.accuracy.isFinite() && result.accuracy >= 0f &&
                result.accuracy <= LIMIT_METERS
            // Rejected fixes leave location empty, so the existing Save check stays disabled.
            location = if (accurateEnough) result else null
            message = when {
                accurateEnough -> "Location ready for this report."
                result == null -> error ?: "Could not find your location. Please try again."
                else -> "Location not accurate enough. Enable precise location or move outdoors and try again."
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        requestingPermission = false
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        // Either permission can request a fix; the accuracy gate decides whether to attach it.
        if (fineGranted || coarseGranted) {
            captureLocation()
        } else {
            location = null
            message = "Location access was not allowed. You can still view the map. " +
                "If Android no longer asks, you can allow location in Android Settings > " +
                "Apps > WaterWatch Laredo > Permissions."
        }
    }

    // Cancel a pending location request when this screen leaves composition.
    DisposableEffect(Unit) {
        onDispose { locationRequest?.cancel() }
    }

    val confirmationLatitude = savedLatitude
    val confirmationLongitude = savedLongitude
    if (confirmationLatitude != null && confirmationLongitude != null) {
        Column(
            modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("✓ SAVED ON THIS DEVICE", style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary)
                    Text("Your observation was saved", style = MaterialTheme.typography.headlineMedium)
                    Text("$savedProblemType · Location attached", style = MaterialTheme.typography.titleMedium)
                    Text("You can find it on the map or in Community on this device. It has not been shared with other residents.")
                }
            }
            Button(
                onClick = { onViewMap(confirmationLatitude, confirmationLongitude) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
            ) { Text("View on map") }
            OutlinedButton(
                onClick = onViewCommunity,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
            ) { Text("View in Community") }
            TextButton(onClick = {
                savedLatitude = null
                savedLongitude = null
                savedProblemType = ""
            }, modifier = Modifier.fillMaxWidth()) { Text("Report another observation") }
            Text("Resident observations are unverified. This app does not replace official city notices.",
                style = MaterialTheme.typography.bodySmall)
        }
        return
    }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Report a water problem", style = MaterialTheme.typography.headlineMedium)
        Text("1. What did you notice?", style = MaterialTheme.typography.titleMedium)
        // Two short rows keep every problem type visible without opening a menu.
        problemTypes.chunked(2).forEach { rowTypes ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowTypes.forEach { type ->
                    FilterChip(
                        selected = problemType == type,
                        onClick = { problemType = type; message = "" },
                        enabled = !saving,
                        label = { Text(type, modifier = Modifier.padding(vertical = 8.dp)) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        guidanceForObservation(problemType)?.let { guidance ->
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Text(guidance.explanation, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { uriHandler.openUri(guidance.sourceUrl) }) { Text("Learn more · EPA ↗") }
                }
            }
        }
        Text("2. Add details (optional)", style = MaterialTheme.typography.titleMedium)

        OutlinedTextField(
            value = note,
            onValueChange = { note = it; message = "" },
            label = { Text("What happened?") },
            placeholder = { Text("For example: unusual taste since this morning") },
            minLines = 2,
            maxLines = 4,
            shape = RoundedCornerShape(16.dp),
            supportingText = { Text("Avoid names, addresses, and private service connection details.") },
            enabled = !saving,
            modifier = Modifier.fillMaxWidth()
        )

        Text("3. Attach the problem location", style = MaterialTheme.typography.titleMedium)
        Text("Attach your location where you noticed the issue.",
            style = MaterialTheme.typography.bodySmall)
        Button(
            enabled = !locating && !saving && !requestingPermission,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            onClick = {
                val fineGranted = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                val coarseGranted = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                if (fineGranted || coarseGranted) {
                    captureLocation()
                } else {
                    location = null
                    message = ""
                    requestingPermission = true
                    // Request both together so Android can offer an accuracy choice.
                    permissionLauncher.launch(arrayOf(
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ))
                }
            }
        ) {
            Text(when {
                requestingPermission -> "Waiting for permission…"
                locating -> "Getting location…"
                else -> "Use my current location"
            })
        }
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                if (location == null) "Location needed before saving" else "✓ Location attached · ready to save",
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Text("Saved on this device when you press Save.",
            style = MaterialTheme.typography.bodySmall)

        Button(
            enabled = problemType.isNotBlank() && location != null && !saving && !locating,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            onClick = {
                val selectedLocation = location ?: return@Button
                val report = Report(
                    problemType = problemType,
                    note = note.trim(),
                    latitude = selectedLocation.latitude,
                    longitude = selectedLocation.longitude,
                    timestamp = System.currentTimeMillis()
                )
                saving = true
                message = ""
                scope.launch {
                    try {
                        reportDao.insert(report)
                        // Show success only after Room finishes saving.
                        savedLatitude = report.latitude
                        savedLongitude = report.longitude
                        savedProblemType = report.problemType
                        problemType = ""
                        note = ""
                        location = null
                        message = "Report saved on this device."
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        message = "Could not save the report. Please try again."
                    } finally {
                        saving = false
                    }
                }
            }
        ) {
            Text(if (saving) "Saving…" else "Save report")
        }
        if (message.isNotEmpty()) {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(12.dp)) {
                Text(message, modifier = Modifier.fillMaxWidth().padding(12.dp))
            }
        }
        Text("Unverified community reports. This app does not replace official city notices.",
            style = MaterialTheme.typography.bodySmall)
    }
}

// Ask for one location fix; this does not start continuous tracking.
internal fun requestReportLocation(
    context: Context,
    cancellationSignal: CancellationSignal,
    onResult: (Location?, String?) -> Unit
) {
    val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    if (!hasFine && !hasCoarse) {
        onResult(null, "Location access is unavailable. Tap the location button to try again.")
        return
    }

    val manager = context.getSystemService(LocationManager::class.java)
    val provider = when {
        hasFine && manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
        manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
        else -> {
            onResult(null, "Turn on device location and try again.")
            return
        }
    }
    try {
        LocationManagerCompat.getCurrentLocation(
            manager, provider, cancellationSignal, ContextCompat.getMainExecutor(context)
        ) { result ->
            onResult(result, if (result == null) "Could not find your location. Please try again." else null)
        }
    } catch (error: SecurityException) {
        onResult(null, "Location permission is unavailable.")
    } catch (error: IllegalArgumentException) {
        onResult(null, "This device's location provider is unavailable.")
    }
}
