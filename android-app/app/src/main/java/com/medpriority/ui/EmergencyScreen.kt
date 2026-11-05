package com.medpriority.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.medpriority.location.LocationHelper
import com.medpriority.network.LocationUpdateRequest
import com.medpriority.network.RetrofitClient
import com.medpriority.ui.components.MedPriorityButton
import com.medpriority.ui.components.MedPriorityCard
import com.medpriority.ui.components.MedPrioritySecondaryButton
import com.medpriority.ui.components.StatusBadge
import com.medpriority.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/** Maximum delay cap for exponential backoff (30 seconds). */
private const val MAX_BACKOFF_MS = 30_000L
/** Initial retry delay for backoff. */
private const val INITIAL_BACKOFF_MS = 2_000L

/**
 * GPS sync state shown to the user.
 */
sealed class GpsSyncState {
    /** Waiting for a GPS fix. */
    object AcquiringFix : GpsSyncState()
    /** Successfully synced to backend. */
    data class Synced(val time: String) : GpsSyncState()
    /** Sent OK but waiting for next cycle. */
    object Streaming : GpsSyncState()
    /** Temporarily offline, will retry. */
    data class OfflineRetry(val attemptInSeconds: Int) : GpsSyncState()
    /** Backend rejected the upload (4xx error, not transient). */
    data class ServerError(val code: Int) : GpsSyncState()
    /** Permission denied. */
    object PermissionDenied : GpsSyncState()
    /** GPS provider unavailable or disabled. */
    object GpsUnavailable : GpsSyncState()
    /** Session was stopped by user. */
    object Paused : GpsSyncState()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmergencyScreen(
    emergencyId: Int,
    onNavigateToHome: () -> Unit
) {
    val context = LocalContext.current
    val locationHelper = remember { LocationHelper(context) }
    val currentLocation by locationHelper.currentLocation.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val apiService = remember { RetrofitClient.getApiService(context) }

    var isTracking by remember { mutableStateOf(false) }
    var locationPermissionGranted by remember { mutableStateOf(false) }
    var syncState by remember { mutableStateOf<GpsSyncState>(GpsSyncState.AcquiringFix) }
    var isEndingEmergency by remember { mutableStateOf(false) }
    var emergencyEnded by remember { mutableStateOf(false) }
    var showEndDialog by remember { mutableStateOf(false) }
    var endError by remember { mutableStateOf<String?>(null) }

    // Consecutive network failure count for exponential backoff
    var consecutiveFailures by remember { mutableIntStateOf(0) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocation = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocation = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        locationPermissionGranted = fineLocation || coarseLocation
        if (locationPermissionGranted) {
            isTracking = true
            locationHelper.startLocationUpdates()
            syncState = GpsSyncState.AcquiringFix
        } else {
            syncState = GpsSyncState.PermissionDenied
        }
    }

    // Auto-request permission on screen entry
    LaunchedEffect(Unit) {
        val fine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (fine) {
            locationPermissionGranted = true
            isTracking = true
            locationHelper.startLocationUpdates()
            syncState = GpsSyncState.AcquiringFix
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    /**
     * Stream location updates to the backend with bounded exponential backoff
     * for transient network errors. Permanent errors (4xx) are not retried.
     *
     * Location updates stop automatically when the emergency is marked ended.
     */
    LaunchedEffect(currentLocation) {
        // Stop uploading once the session is ended or paused by user
        if (emergencyEnded || !isTracking) return@LaunchedEffect

        currentLocation?.let { loc ->
            if (emergencyId <= 0) return@let

            // Compute backoff delay — cap at MAX_BACKOFF_MS with ±10% jitter
            val backoffMs = if (consecutiveFailures == 0) 0L else {
                val base = INITIAL_BACKOFF_MS * (1L shl (consecutiveFailures - 1).coerceAtMost(4))
                val capped = base.coerceAtMost(MAX_BACKOFF_MS)
                val jitter = (capped * 0.1 * (Math.random() * 2 - 1)).toLong()
                (capped + jitter).coerceAtLeast(0L)
            }

            if (backoffMs > 0L) {
                // Show countdown to user
                val totalSecs = (backoffMs / 1000).toInt().coerceAtLeast(1)
                var remaining = totalSecs
                while (remaining > 0 && isActive && isTracking && !emergencyEnded) {
                    syncState = GpsSyncState.OfflineRetry(remaining)
                    delay(1_000L)
                    remaining--
                }
                if (!isTracking || emergencyEnded) return@let
            }

            try {
                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val req = LocationUpdateRequest(
                    latitude = loc.latitude,
                    longitude = loc.longitude,
                    accuracy = loc.accuracy,
                    recordedAt = sdf.format(Date(loc.time))
                )
                syncState = GpsSyncState.Streaming
                val response = apiService.sendLocation(emergencyId, req)

                when {
                    response.isSuccessful -> {
                        consecutiveFailures = 0
                        val timeString = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                        syncState = GpsSyncState.Synced(timeString)
                    }
                    response.code() == 409 -> {
                        // Session ended server-side; stop uploading, don't retry
                        isTracking = false
                        locationHelper.stopAndClear()
                        emergencyEnded = true
                        syncState = GpsSyncState.ServerError(409)
                    }
                    response.code() in 400..499 -> {
                        // Permanent client error — do not retry
                        syncState = GpsSyncState.ServerError(response.code())
                        isTracking = false
                        locationHelper.stopAndClear()
                    }
                    else -> {
                        // Transient server error — schedule backoff
                        consecutiveFailures++
                        syncState = GpsSyncState.OfflineRetry(
                            (INITIAL_BACKOFF_MS.coerceAtMost(MAX_BACKOFF_MS) / 1000).toInt()
                        )
                    }
                }
            } catch (e: Exception) {
                // Network I/O failure — transient
                consecutiveFailures++
                val nextBackoffSec = (INITIAL_BACKOFF_MS * (1L shl (consecutiveFailures - 1).coerceAtMost(4))
                    .coerceAtMost(MAX_BACKOFF_MS) / 1000).toInt()
                syncState = GpsSyncState.OfflineRetry(nextBackoffSec)
            }
        }
    }

    // On leave — always stop location updates
    DisposableEffect(Unit) {
        onDispose {
            locationHelper.stopAndClear()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Emergency Session",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                },
                navigationIcon = {
                    TextButton(onClick = onNavigateToHome) {
                        Text("‹ Dashboard", fontSize = 15.sp, color = AccentBlue, fontWeight = FontWeight.SemiBold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundLight)
            )
        },
        containerColor = BackgroundLight
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ─── SOS Active Indicator Banner ────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (emergencyEnded) SuccessGreenSubtle else EmergencyRedSubtle,
                        RoundedCornerShape(16.dp)
                    )
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (emergencyEnded) {
                        StatusBadge(text = "SESSION ENDED", isPositive = true)
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Emergency Resolved",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = SuccessGreen
                        )
                    } else {
                        StatusBadge(text = "LIVE SOS BROADCAST", isPositive = false)
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Emergency Active",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = EmergencyRed
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Session ID: #$emergencyId",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ─── GPS Telemetry Card ──────────────────────────────────────────
            MedPriorityCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Live GPS Telemetry",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    GpsSyncStatusBadge(state = syncState)
                }

                Spacer(modifier = Modifier.height(16.dp))

                when {
                    syncState == GpsSyncState.PermissionDenied -> {
                        GpsInfoRow(
                            icon = "🚫",
                            message = "Location permission was denied. Tap Settings to grant access.",
                            color = EmergencyRed
                        )
                    }
                    syncState == GpsSyncState.GpsUnavailable -> {
                        GpsInfoRow(
                            icon = "📡",
                            message = "GPS provider unavailable. Enable Location in device Settings.",
                            color = WarningOrange
                        )
                    }
                    currentLocation != null -> {
                        TelemetryRow(
                            label = "Latitude",
                            value = String.format(Locale.US, "%.6f°", currentLocation!!.latitude)
                        )
                        HorizontalDivider(color = BorderSubtle, modifier = Modifier.padding(vertical = 8.dp))
                        TelemetryRow(
                            label = "Longitude",
                            value = String.format(Locale.US, "%.6f°", currentLocation!!.longitude)
                        )
                        HorizontalDivider(color = BorderSubtle, modifier = Modifier.padding(vertical = 8.dp))
                        TelemetryRow(
                            label = "Accuracy",
                            value = "±${currentLocation!!.accuracy.toInt()}m"
                        )
                    }
                    isTracking -> {
                        GpsInfoRow(
                            icon = "🛰️",
                            message = "Acquiring GPS fix… Stay in open sky for faster lock.",
                            color = TextSecondary
                        )
                    }
                    else -> {
                        GpsInfoRow(
                            icon = "⏸",
                            message = "GPS streaming paused.",
                            color = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Backend sync status row
                BackendSyncRow(state = syncState)
            }

            // ─── Offline warning ────────────────────────────────────────────
            AnimatedVisibility(
                visible = syncState is GpsSyncState.OfflineRetry,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(WarningOrangeSubtle, RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    val secs = (syncState as? GpsSyncState.OfflineRetry)?.attemptInSeconds ?: 0
                    Text(
                        text = "⚠ Network unavailable — retrying in ${secs}s. Your SOS is still active on the server.",
                        fontSize = 13.sp,
                        color = WarningOrange,
                        lineHeight = 18.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            if (!emergencyEnded) {
                // Pause / Resume toggle
                MedPrioritySecondaryButton(
                    text = if (isTracking) "Pause GPS Streaming" else "Resume GPS Streaming",
                    contentColor = if (isTracking) WarningOrange else AccentBlue,
                    borderColor = BorderSubtle,
                    onClick = {
                        if (isTracking) {
                            isTracking = false
                            locationHelper.stopLocationUpdates()
                            consecutiveFailures = 0
                            syncState = GpsSyncState.Paused
                        } else {
                            isTracking = true
                            consecutiveFailures = 0
                            locationHelper.startLocationUpdates()
                            syncState = GpsSyncState.AcquiringFix
                        }
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // End Emergency CTA
                MedPriorityButton(
                    text = if (isEndingEmergency) "Ending…" else "End Emergency Session",
                    containerColor = EmergencyRed,
                    contentColor = SurfaceCard,
                    isLoading = isEndingEmergency,
                    onClick = { showEndDialog = true }
                )
            } else {
                // Session over — go home
                MedPriorityButton(
                    text = "Return to Dashboard",
                    containerColor = AccentBlue,
                    contentColor = SurfaceCard,
                    onClick = onNavigateToHome
                )
            }

            if (endError != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = endError!!, color = EmergencyRed, fontSize = 13.sp)
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // ─── Confirmation Dialog to End Emergency ───────────────────────────────
    if (showEndDialog) {
        AlertDialog(
            onDismissRequest = { if (!isEndingEmergency) showEndDialog = false },
            title = { Text("End Emergency Session", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Are you sure you want to resolve Emergency Session #$emergencyId? " +
                    "Live location broadcasting will stop immediately.",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !isEndingEmergency,
                    onClick = {
                        isEndingEmergency = true
                        endError = null
                        coroutineScope.launch {
                            // Stop GPS first — before the API call — to prevent
                            // any in-flight location update from being sent after end.
                            locationHelper.stopAndClear()
                            isTracking = false
                            consecutiveFailures = 0

                            try {
                                if (emergencyId > 0) {
                                    val res = apiService.endEmergency(emergencyId)
                                    if (res.isSuccessful) {
                                        emergencyEnded = true
                                        syncState = GpsSyncState.Paused
                                    } else if (res.code() == 409) {
                                        // Already ended server-side — treat as success
                                        emergencyEnded = true
                                        syncState = GpsSyncState.Paused
                                    } else {
                                        endError = "Server returned ${res.code()}. Session may already be ended."
                                        emergencyEnded = true
                                    }
                                } else {
                                    emergencyEnded = true
                                }
                                showEndDialog = false
                                if (emergencyEnded) onNavigateToHome()
                            } catch (e: Exception) {
                                // Network error — session still active on server; warn user
                                endError = "Network error. Could not confirm end on server. Return to dashboard and check session status."
                                showEndDialog = false
                            } finally {
                                isEndingEmergency = false
                            }
                        }
                    }
                ) {
                    Text("End Session", color = EmergencyRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(enabled = !isEndingEmergency, onClick = { showEndDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = SurfaceCard,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

// ─── Composable Helpers ──────────────────────────────────────────────────────

@Composable
private fun GpsSyncStatusBadge(state: GpsSyncState) {
    val label = when (state) {
        is GpsSyncState.Synced -> "Synced"
        is GpsSyncState.Streaming -> "Streaming"
        is GpsSyncState.AcquiringFix -> "Acquiring"
        is GpsSyncState.OfflineRetry -> "Offline"
        is GpsSyncState.PermissionDenied -> "No Permission"
        is GpsSyncState.GpsUnavailable -> "GPS Off"
        is GpsSyncState.ServerError -> "Error ${state.code}"
        is GpsSyncState.Paused -> "Paused"
    }
    StatusBadge(
        text = label,
        isPositive = state is GpsSyncState.Synced || state is GpsSyncState.Streaming
    )
}

@Composable
private fun BackendSyncRow(state: GpsSyncState) {
    val (label, color) = when (state) {
        is GpsSyncState.Synced -> "Synced at ${state.time}" to AccentBlue
        is GpsSyncState.Streaming -> "Uploading…" to AccentBlue
        is GpsSyncState.AcquiringFix -> "Waiting for GPS…" to TextSecondary
        is GpsSyncState.OfflineRetry -> "Offline — retry in ${state.attemptInSeconds}s" to WarningOrange
        is GpsSyncState.PermissionDenied -> "Permission denied" to EmergencyRed
        is GpsSyncState.GpsUnavailable -> "GPS unavailable" to EmergencyRed
        is GpsSyncState.ServerError -> "Server error (${state.code})" to EmergencyRed
        is GpsSyncState.Paused -> "Paused" to TextSecondary
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BackgroundLight, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("Backend Status:", fontSize = 12.sp, color = TextSecondary)
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
    }
}

@Composable
private fun GpsInfoRow(icon: String, message: String, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text = icon, fontSize = 20.sp)
        Text(
            text = message,
            fontSize = 13.sp,
            color = color,
            lineHeight = 18.sp
        )
    }
}

@Composable
private fun TelemetryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 14.sp, color = TextSecondary)
        Text(text = value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
    }
}
