package com.medpriority.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medpriority.R
import com.medpriority.network.*
import com.medpriority.ui.components.MedPriorityButton
import com.medpriority.ui.components.MedPriorityCard
import com.medpriority.ui.components.MedPrioritySecondaryButton
import com.medpriority.ui.components.StatusBadge
import com.medpriority.ui.theme.*
import com.medpriority.utils.TokenManager
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    tokenManager: TokenManager,
    onNavigateToVehicles: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToEmergency: (emergencyId: Int) -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val apiService = remember { RetrofitClient.getApiService(context) }
    val coroutineScope = rememberCoroutineScope()

    var userName by remember { mutableStateOf<String?>("User") }
    var vehicles by remember { mutableStateOf<List<VehicleDto>>(emptyList()) }
    var activeEmergency by remember { mutableStateOf<EmergencySessionDto?>(null) }
    var emergencyHistory by remember { mutableStateOf<List<EmergencySessionDto>>(emptyList()) }
    var isLoadingData by remember { mutableStateOf(true) }
    var isStartingSOS by remember { mutableStateOf(false) }

    var showVehicleSelectDialog by remember { mutableStateOf(false) }
    var showNoVehicleDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun refreshDashboard() {
        isLoadingData = true
        errorMessage = null
        coroutineScope.launch {
            try {
                // 1. Fetch user profile
                val userRes = apiService.getCurrentUser()
                if (userRes.isSuccessful && userRes.body() != null) {
                    userName = userRes.body()!!.name
                } else if (userRes.code() == 401) {
                    onLogout()
                    return@launch
                }

                // 2. Fetch vehicles
                val vehRes = apiService.getVehicles()
                if (vehRes.isSuccessful && vehRes.body() != null) {
                    vehicles = vehRes.body()!!.data
                }

                // 3. Check for active emergency
                val emRes = apiService.getActiveEmergency()
                if (emRes.isSuccessful && emRes.body() != null) {
                    activeEmergency = emRes.body()!!.data
                }

                // 4. Fetch emergency history
                val histRes = apiService.getEmergencyHistory(limit = 5)
                if (histRes.isSuccessful && histRes.body() != null) {
                    emergencyHistory = histRes.body()!!.data
                }
            } catch (e: Exception) {
                errorMessage = "Could not sync latest data. Pull down to refresh."
            } finally {
                isLoadingData = false
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshDashboard()
    }

    fun startSOSSession(vehicleId: Int) {
        isStartingSOS = true
        errorMessage = null
        coroutineScope.launch {
            try {
                val res = apiService.startEmergency(EmergencyStartRequest(vehicleId))
                if (res.isSuccessful && res.body() != null) {
                    val session = res.body()!!.data
                    activeEmergency = session
                    showVehicleSelectDialog = false
                    onNavigateToEmergency(session.id)
                } else {
                    errorMessage = "Failed to start emergency session. Please retry."
                }
            } catch (e: Exception) {
                errorMessage = "Network error. Unable to trigger SOS."
            } finally {
                isStartingSOS = false
            }
        }
    }

    fun handleSOSClick() {
        if (activeEmergency != null) {
            onNavigateToEmergency(activeEmergency!!.id)
            return
        }
        if (vehicles.isEmpty()) {
            showNoVehicleDialog = true
        } else if (vehicles.size == 1) {
            // Automatically start SOS for single registered vehicle
            startSOSSession(vehicles.first().id)
        } else {
            showVehicleSelectDialog = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundLight)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 28.dp)
        ) {
            // Header Row: Brand Logo, Greeting & Profile Action
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.medpriority_logo),
                        contentDescription = "MedPriority Logo",
                        modifier = Modifier.size(44.dp),
                        contentScale = ContentScale.Fit
                    )
                    Column {
                        Text(
                            text = "MedPriority",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentBlue,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isLoadingData) "Loading..." else "Hello, ${userName ?: "User"}",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            letterSpacing = (-0.4).sp
                        )
                    }
                }

                IconButton(
                    onClick = onNavigateToProfile,
                    modifier = Modifier
                        .size(42.dp)
                        .background(SurfaceCard, CircleShape)
                ) {
                    Text(
                        text = (userName?.firstOrNull() ?: 'U').toString(),
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = AccentBlue
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Active Emergency Status Banner (if active)
            if (activeEmergency != null) {
                MedPriorityCard(
                    backgroundColor = EmergencyRedSubtle,
                    borderColor = EmergencyRed.copy(alpha = 0.3f),
                    onClick = { onNavigateToEmergency(activeEmergency!!.id) }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            StatusBadge(text = "EMERGENCY ACTIVE", isPositive = false)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Emergency Session #${activeEmergency!!.id}",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = EmergencyRed
                            )
                            Text(
                                text = "Tap to resume GPS tracking",
                                fontSize = 13.sp,
                                color = TextSecondary
                            )
                        }
                        Text(
                            text = "View →",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = EmergencyRed
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Primary Emergency SOS Card
            MedPriorityCard(
                backgroundColor = SurfaceCard,
                borderColor = BorderSubtle
            ) {
                Text(
                    text = "Emergency Visibility",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextSecondary,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Need Immediate Medical Priority?",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Activating SOS broadcasts your live GPS position to surrounding emergency responders and clears your path.",
                    fontSize = 14.sp,
                    color = TextSecondary,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                MedPriorityButton(
                    text = if (activeEmergency != null) "Resume Active SOS" else "Activate Emergency SOS",
                    containerColor = EmergencyRed,
                    contentColor = SurfaceCard,
                    isLoading = isStartingSOS,
                    onClick = { handleSOSClick() }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Vehicle Overview Card
            MedPriorityCard(
                backgroundColor = SurfaceCard,
                borderColor = BorderSubtle
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Registered Vehicles",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (vehicles.isEmpty()) "No vehicles linked" else "${vehicles.size} vehicle(s) ready",
                            fontSize = 13.sp,
                            color = TextSecondary
                        )
                    }

                    TextButton(onClick = onNavigateToVehicles) {
                        Text(
                            text = if (vehicles.isEmpty()) "Add Vehicle" else "Manage",
                            color = AccentBlue,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    }
                }

                if (vehicles.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    val primary = vehicles.first()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(BackgroundLight, RoundedCornerShape(12.dp))
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = primary.vehicleNumber,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = TextPrimary
                            )
                            Text(
                                text = "${primary.vehicleColor} ${primary.vehicleType}",
                                fontSize = 13.sp,
                                color = TextSecondary
                            )
                        }
                        StatusBadge(
                            text = if (primary.isVerified) "Verified" else "Pending",
                            isPositive = primary.isVerified
                        )
                    }
                }
            }

            // Emergency History Card
            if (emergencyHistory.isNotEmpty()) {
                Spacer(modifier = Modifier.height(24.dp))
                MedPriorityCard(
                    backgroundColor = SurfaceCard,
                    borderColor = BorderSubtle
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Emergency Session History",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${emergencyHistory.size} logged session(s)",
                                fontSize = 13.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    emergencyHistory.take(3).forEach { session ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .background(BackgroundLight, RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Session #${session.id}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Vehicle #${session.vehicleId} • ${session.startedAt.take(10)}",
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                            }
                            StatusBadge(
                                text = session.status,
                                isPositive = session.status == "RESOLVED" || session.status == "ENDED"
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Quick Actions & Logout
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MedPrioritySecondaryButton(
                    text = "My Vehicles",
                    onClick = onNavigateToVehicles,
                    modifier = Modifier.weight(1f)
                )
                MedPrioritySecondaryButton(
                    text = "Account Profile",
                    onClick = onNavigateToProfile,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            MedPrioritySecondaryButton(
                text = "Log Out",
                contentColor = EmergencyRed,
                borderColor = BorderSubtle,
                onClick = { showLogoutDialog = true }
            )

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = errorMessage!!,
                    color = EmergencyRed,
                    fontSize = 13.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }

    // Dialog: No Vehicles Registered
    if (showNoVehicleDialog) {
        AlertDialog(
            onDismissRequest = { showNoVehicleDialog = false },
            title = { Text("Vehicle Required", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "You must register at least one vehicle before initiating an Emergency SOS session.",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showNoVehicleDialog = false
                        onNavigateToVehicles()
                    }
                ) {
                    Text("Register Vehicle", color = AccentBlue, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNoVehicleDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = SurfaceCard,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Dialog: Select Vehicle for SOS
    if (showVehicleSelectDialog) {
        AlertDialog(
            onDismissRequest = { showVehicleSelectDialog = false },
            title = { Text("Select Emergency Vehicle", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "Which vehicle are you currently traveling in?",
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    vehicles.forEach { v ->
                        Button(
                            onClick = { startSOSSession(v.id) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BackgroundLight)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${v.vehicleNumber} (${v.vehicleType})",
                                    color = TextPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(text = "Select →", color = AccentBlue, fontSize = 13.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showVehicleSelectDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = SurfaceCard,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Dialog: Confirm Logout
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Log Out", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Are you sure you want to log out of MedPriority?",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        tokenManager.clearToken()
                        onLogout()
                    }
                ) {
                    Text("Log Out", color = EmergencyRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = SurfaceCard,
            shape = RoundedCornerShape(16.dp)
        )
    }
}
