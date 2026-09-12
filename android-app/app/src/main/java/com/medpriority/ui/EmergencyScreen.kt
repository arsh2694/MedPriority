package com.medpriority.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import com.medpriority.location.LocationHelper
import com.medpriority.network.LocationUpdateRequest
import com.medpriority.network.RetrofitClient

@Composable
fun EmergencyScreen(emergencyId: Int) {
    val context = LocalContext.current
    val locationHelper = remember { LocationHelper(context) }
    val currentLocation by locationHelper.currentLocation.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val apiService = RetrofitClient.getApiService(context)

    var isTracking by remember { mutableStateOf(false) }
    var locationPermissionGranted by remember { mutableStateOf(false) }
    var syncStatus by remember { mutableStateOf("Standby") }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocation = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocation = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        locationPermissionGranted = fineLocation || coarseLocation
        if (locationPermissionGranted) {
            isTracking = true
            locationHelper.startLocationUpdates()
        } else {
            syncStatus = "Permission Denied"
        }
    }

    // Effect to send location to backend when it changes
    LaunchedEffect(currentLocation) {
        currentLocation?.let { loc ->
            try {
                syncStatus = "Syncing..."
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
                    syncStatus = "Last sync: $timeString"
                } else {
                    syncStatus = "Sync Failed: \${response.code()}"
                }
            } catch (e: Exception) {
                syncStatus = "Network Error"
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            locationHelper.stopLocationUpdates()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Emergency Active", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF3B30))
        Spacer(modifier = Modifier.height(8.dp))
        Text("Session ID: \$emergencyId", fontSize = 16.sp, color = Color(0xFF86868B))
        
        Spacer(modifier = Modifier.height(48.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF2F2F7)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("GPS Status", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                Spacer(modifier = Modifier.height(16.dp))
                if (currentLocation != null) {
                    Text("Lat: \${currentLocation!!.latitude}", fontSize = 16.sp)
                    Text("Lng: \${currentLocation!!.longitude}", fontSize = 16.sp)
                    Text("Acc: \${currentLocation!!.accuracy}m", fontSize = 16.sp)
                } else {
                    Text(if (isTracking) "Acquiring location..." else "Location stopped", fontSize = 16.sp)
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(syncStatus, fontSize = 14.sp, color = Color(0xFF34C759))
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = {
                if (!isTracking) {
                    // Check permissions
                    val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    if (fine) {
                        isTracking = true
                        locationHelper.startLocationUpdates()
                    } else {
                        permissionLauncher.launch(arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ))
                    }
                } else {
                    isTracking = false
                    locationHelper.stopLocationUpdates()
                    syncStatus = "Stopped"
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isTracking) Color(0xFFFF3B30) else Color(0xFF007AFF)
            )
        ) {
            Text(if (isTracking) "Stop Sharing Location" else "Start Sharing Location", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}
