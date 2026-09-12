package com.medpriority.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

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
    var syncStatus by remember { mutableStateOf("Standby") }
    var isEndingEmergency by remember { mutableStateOf(false) }
    var showEndDialog by remember { mutableStateOf(false) }
    var endError by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocation = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocation = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        locationPermissionGranted = fineLocation || coarseLocation
        if (locationPermissionGranted) {
            isTracking = true
            locationHelper.startLocationUpdates()
            syncStatus = "Broadcasting GPS"
        } else {
            syncStatus = "Location Permission Denied"
        }
    }

    // Auto-request permission on screen entry
    LaunchedEffect(Unit) {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (fine) {
            locationPermissionGranted = true
            isTracking = true
            locationHelper.startLocationUpdates()
            syncStatus = "Broadcasting GPS"
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    // Stream location updates to backend
    LaunchedEffect(currentLocation) {
        currentLocation?.let { loc ->
            if (isTracking && emergencyId > 0) {
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
                    val response = apiService.sendLocation(emergencyId, req)
                    if (response.isSuccessful) {
                        val timeString = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                        syncStatus = "Synced at $timeString"
                    } else {
                        syncStatus = "Sync code: ${response.code()}"
                    }
                } catch (e: Exception) {
                    syncStatus = "Network sync pending"
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            locationHelper.stopLocationUpdates()
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
            // SOS Active Indicator Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(EmergencyRedSubtle, RoundedCornerShape(16.dp))
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    StatusBadge(text = "LIVE SOS BROADCAST", isPositive = false)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Emergency Active",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = EmergencyRed
                    )
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

            // Live Telemetry / GPS Status Card
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
                    StatusBadge(
                        text = if (isTracking) "Streaming" else "Paused",
                        isPositive = isTracking
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (currentLocation != null) {
                    TelemetryRow(label = "Latitude", value = String.format(Locale.US, "%.6f°", currentLocation!!.latitude))
                    Divider(color = BorderSubtle, modifier = Modifier.padding(vertical = 8.dp))
                    TelemetryRow(label = "Longitude", value = String.format(Locale.US, "%.6f°", currentLocation!!.longitude))
                    Divider(color = BorderSubtle, modifier = Modifier.padding(vertical = 8.dp))
                    TelemetryRow(label = "GPS Accuracy", value = "±${currentLocation!!.accuracy.toInt()}m")
                } else {
                    Text(
                        text = if (isTracking) "Acquiring GPS fix..." else "Location streaming paused",
                        fontSize = 14.sp,
                        color = TextSecondary
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BackgroundLight, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Backend Status:", fontSize = 12.sp, color = TextSecondary)
                    Text(syncStatus, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = AccentBlue)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Tracking Toggle Button
            MedPrioritySecondaryButton(
                text = if (isTracking) "Pause GPS Streaming" else "Resume GPS Streaming",
                contentColor = if (isTracking) WarningOrange else AccentBlue,
                borderColor = BorderSubtle,
                onClick = {
                    if (isTracking) {
                        isTracking = false
                        locationHelper.stopLocationUpdates()
                        syncStatus = "Paused"
                    } else {
                        isTracking = true
                        locationHelper.startLocationUpdates()
                        syncStatus = "Broadcasting GPS"
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // End Emergency Action
            MedPriorityButton(
                text = "End Emergency Session",
                containerColor = EmergencyRed,
                contentColor = SurfaceCard,
                isLoading = isEndingEmergency,
                onClick = { showEndDialog = true }
            )

            if (endError != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = endError!!, color = EmergencyRed, fontSize = 13.sp)
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Confirmation Dialog to End Emergency
    if (showEndDialog) {
        AlertDialog(
            onDismissRequest = { if (!isEndingEmergency) showEndDialog = false },
            title = { Text("End Emergency Session", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Are you sure you want to resolve and conclude Emergency Session #$emergencyId? Live location broadcasting will stop immediately.",
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
                            try {
                                locationHelper.stopLocationUpdates()
                                isTracking = false
                                if (emergencyId > 0) {
                                    apiService.endEmergency(emergencyId)
                                }
                                showEndDialog = false
                                onNavigateToHome()
                            } catch (e: Exception) {
                                endError = "Could not conclude session on backend. Navigating to Dashboard."
                                showEndDialog = false
                                onNavigateToHome()
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
