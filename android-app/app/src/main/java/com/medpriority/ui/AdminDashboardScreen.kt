package com.medpriority.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medpriority.network.AdminEmergencyDetailDto
import com.medpriority.network.EmergencySessionDto
import com.medpriority.network.RetrofitClient
import com.medpriority.network.VehicleDto
import com.medpriority.ui.components.MedPriorityCard
import com.medpriority.ui.components.StatusBadge
import com.medpriority.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun AdminDashboardScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val apiService = remember { RetrofitClient.getApiService(context) }
    val coroutineScope = rememberCoroutineScope()

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Active, 1: Vehicles, 2: History
    var activeEmergencies by remember { mutableStateOf<List<AdminEmergencyDetailDto>>(emptyList()) }
    var vehiclesList by remember { mutableStateOf<List<VehicleDto>>(emptyList()) }
    var historyList by remember { mutableStateOf<List<EmergencySessionDto>>(emptyList()) }

    // End session dialog state
    var sessionToEnd by remember { mutableStateOf<AdminEmergencyDetailDto?>(null) }

    // Data fetching functions
    fun fetchActiveEmergencies() {
        coroutineScope.launch {
            try {
                val res = apiService.getAdminActiveEmergencies()
                if (res.isSuccessful && res.body() != null) {
                    activeEmergencies = res.body()!!.data
                }
            } catch (_: Exception) {}
        }
    }

    fun fetchVehicles() {
        coroutineScope.launch {
            try {
                val res = apiService.getAdminVehicles()
                if (res.isSuccessful && res.body() != null) {
                    vehiclesList = res.body()!!.data
                }
            } catch (_: Exception) {}
        }
    }

    fun fetchHistory() {
        coroutineScope.launch {
            try {
                val res = apiService.getEmergencyHistory(limit = 40)
                if (res.isSuccessful && res.body() != null) {
                    historyList = res.body()!!.data
                }
            } catch (_: Exception) {}
        }
    }

    // Auto-refresh active emergencies every 4 seconds
    LaunchedEffect(Unit) {
        fetchActiveEmergencies()
        fetchVehicles()
        fetchHistory()
        while (isActive) {
            delay(4000)
            fetchActiveEmergencies()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = BackgroundLight
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp)
        ) {
            // Top App Bar Navigation
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(
                    onClick = onNavigateBack,
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        text = "‹ Back to User App",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AccentBlue
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .background(Color(0xFFE8F5E9), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(Color(0xFF2E7D32), CircleShape)
                    )
                    Text(
                        text = "DISPATCHER",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2E7D32)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Screen Title
            Text(
                text = "Emergency Operations",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                letterSpacing = (-0.5).sp
            )
            Text(
                text = "Live telemetry monitor and fleet verification console",
                fontSize = 14.sp,
                color = TextSecondary,
                modifier = Modifier.padding(top = 2.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Quick Stats Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Active Card
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = if (activeEmergencies.isNotEmpty()) Color(0xFFFFF0F0) else SurfaceCard),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "ACTIVE SOS",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (activeEmergencies.isNotEmpty()) EmergencyRed else TextSecondary
                        )
                        Text(
                            text = "${activeEmergencies.size}",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (activeEmergencies.isNotEmpty()) EmergencyRed else TextPrimary,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }

                // Pending Vehicles Card
                val unverifiedCount = vehiclesList.count { !it.isVerified }
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = if (unverifiedCount > 0) Color(0xFFFFF8E1) else SurfaceCard),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "PENDING",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (unverifiedCount > 0) Color(0xFFF57F17) else TextSecondary
                        )
                        Text(
                            text = "$unverifiedCount",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (unverifiedCount > 0) Color(0xFFF57F17) else TextPrimary,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }

                // Fleet Card
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "FLEET",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary
                        )
                        Text(
                            text = "${vehiclesList.size}",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Tab Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFE5E5EA), RoundedCornerShape(12.dp))
                    .padding(3.dp)
            ) {
                TabButton(
                    text = "Live SOS (${activeEmergencies.size})",
                    isSelected = selectedTab == 0,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        selectedTab = 0
                        fetchActiveEmergencies()
                    }
                )
                TabButton(
                    text = "Vehicles (${vehiclesList.size})",
                    isSelected = selectedTab == 1,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        selectedTab = 1
                        fetchVehicles()
                    }
                )
                TabButton(
                    text = "History",
                    isSelected = selectedTab == 2,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        selectedTab = 2
                        fetchHistory()
                    }
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // TAB 0: Active Emergencies
            if (selectedTab == 0) {
                if (activeEmergencies.isEmpty()) {
                    MedPriorityCard {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(text = "🛡️", fontSize = 32.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "All Clear — No Active SOS",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "System standing by. New emergency beacons will trigger automatically.",
                                fontSize = 13.sp,
                                color = TextSecondary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 4.dp, start = 16.dp, end = 16.dp)
                            )
                        }
                    }
                } else {
                    activeEmergencies.forEach { emergency ->
                        MedPriorityCard(
                            backgroundColor = Color(0xFFFFF2F2),
                            borderColor = Color(0xFFFFCDD2)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .background(EmergencyRed, CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = emergency.vehicleNumber,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFFFFEBEE), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "SESSION #${emergency.id}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = EmergencyRed
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = "Driver: ${emergency.userName} (${emergency.userEmail})",
                                fontSize = 13.sp,
                                color = TextSecondary
                            )
                            Text(
                                text = "Vehicle: ${emergency.vehicleColor} ${emergency.vehicleType}",
                                fontSize = 13.sp,
                                color = TextSecondary
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Telemetry Box
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.White, RoundedCornerShape(10.dp))
                                    .padding(10.dp)
                            ) {
                                Column {
                                    Text(
                                        text = "LIVE GPS TELEMETRY",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextSecondary
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    if (emergency.latestLatitude != null && emergency.latestLongitude != null) {
                                        Text(
                                            text = "${emergency.latestLatitude}° N, ${emergency.latestLongitude}° E",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = TextPrimary
                                        )
                                        Text(
                                            text = "Accuracy: ±${emergency.latestAccuracy ?: 10f}m",
                                            fontSize = 11.sp,
                                            color = TextSecondary
                                        )
                                    } else {
                                        Text(
                                            text = "Awaiting first GPS coordinate beacon...",
                                            fontSize = 13.sp,
                                            color = TextSecondary
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Button(
                                onClick = { sessionToEnd = emergency },
                                colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "End Emergency Session",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }

            // TAB 1: Vehicles Queue
            if (selectedTab == 1) {
                if (vehiclesList.isEmpty()) {
                    MedPriorityCard {
                        Text(
                            text = "No registered vehicles found.",
                            fontSize = 14.sp,
                            color = TextSecondary,
                            modifier = Modifier.padding(vertical = 24.dp)
                        )
                    }
                } else {
                    vehiclesList.forEach { v ->
                        MedPriorityCard {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = v.vehicleNumber,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        StatusBadge(
                                            text = if (v.isVerified) "VERIFIED" else "PENDING",
                                            isPositive = v.isVerified
                                        )
                                    }
                                    Text(
                                        text = "${v.ownerName} • ${v.vehicleColor} ${v.vehicleType}",
                                        fontSize = 13.sp,
                                        color = TextSecondary,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }

                                if (!v.isVerified) {
                                    Button(
                                        onClick = {
                                            coroutineScope.launch {
                                                try {
                                                    val res = apiService.verifyVehicle(v.id)
                                                    if (res.isSuccessful) {
                                                        fetchVehicles()
                                                    }
                                                } catch (_: Exception) {}
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = "Verify",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }
            }

            // TAB 2: Emergency History
            if (selectedTab == 2) {
                if (historyList.isEmpty()) {
                    MedPriorityCard {
                        Text(
                            text = "No past emergency records found.",
                            fontSize = 14.sp,
                            color = TextSecondary,
                            modifier = Modifier.padding(vertical = 24.dp)
                        )
                    }
                } else {
                    historyList.forEach { h ->
                        MedPriorityCard {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Session #${h.id}",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "Vehicle ID: ${h.vehicleId} • User ID: ${h.userId}",
                                        fontSize = 12.sp,
                                        color = TextSecondary
                                    )
                                    Text(
                                        text = "Started: ${h.startedAt.take(19).replace('T', ' ')}",
                                        fontSize = 11.sp,
                                        color = TextSecondary
                                    )
                                }

                                StatusBadge(
                                    text = h.status,
                                    isPositive = h.status == "ENDED"
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }

    // Confirmation dialog for ending emergency
    if (sessionToEnd != null) {
        val s = sessionToEnd!!
        AlertDialog(
            onDismissRequest = { sessionToEnd = null },
            title = { Text(text = "End Emergency Session #${s.id}?") },
            text = { Text("Are you sure you want to terminate this active emergency broadcast for ${s.vehicleNumber} (${s.userName})?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val id = s.id
                        sessionToEnd = null
                        coroutineScope.launch {
                            try {
                                val res = apiService.endEmergency(id)
                                if (res.isSuccessful) {
                                    fetchActiveEmergencies()
                                    fetchHistory()
                                }
                            } catch (_: Exception) {}
                        }
                    }
                ) {
                    Text(text = "End Session", color = EmergencyRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { sessionToEnd = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun TabButton(
    text: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .background(if (isSelected) Color.White else Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) TextPrimary else TextSecondary
        )
    }
}
