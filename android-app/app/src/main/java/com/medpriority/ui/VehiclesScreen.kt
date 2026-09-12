package com.medpriority.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.medpriority.network.*
import com.medpriority.ui.components.MedPriorityButton
import com.medpriority.ui.components.MedPriorityCard
import com.medpriority.ui.components.MedPrioritySecondaryButton
import com.medpriority.ui.components.MedPriorityTextField
import com.medpriority.ui.components.StatusBadge
import com.medpriority.ui.theme.*
import kotlinx.coroutines.launch
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehiclesScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val apiService = remember { RetrofitClient.getApiService(context) }
    val coroutineScope = rememberCoroutineScope()

    var vehicles by remember { mutableStateOf<List<VehicleDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var generalError by remember { mutableStateOf<String?>(null) }

    // Dialog States
    var showAddDialog by remember { mutableStateOf(false) }
    var editingVehicle by remember { mutableStateOf<VehicleDto?>(null) }
    var deletingVehicle by remember { mutableStateOf<VehicleDto?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }

    fun loadVehicles() {
        isLoading = true
        generalError = null
        coroutineScope.launch {
            try {
                val res = apiService.getVehicles()
                if (res.isSuccessful && res.body() != null) {
                    vehicles = res.body()!!.data
                } else {
                    generalError = "Failed to load vehicles (${res.code()})"
                }
            } catch (e: Exception) {
                generalError = "Network error. Please try again."
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        loadVehicles()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "My Vehicles",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                },
                navigationIcon = {
                    TextButton(onClick = onNavigateBack) {
                        Text("‹ Back", fontSize = 16.sp, color = AccentBlue, fontWeight = FontWeight.SemiBold)
                    }
                },
                actions = {
                    TextButton(onClick = { showAddDialog = true }) {
                        Text("+ Add", fontSize = 16.sp, color = AccentBlue, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundLight)
            )
        },
        containerColor = BackgroundLight
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (isLoading && vehicles.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AccentBlue)
                }
            } else if (vehicles.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "No Vehicles Registered",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Add your emergency response or transport vehicle to enable priority routing and visibility.",
                        fontSize = 14.sp,
                        color = TextSecondary,
                        lineHeight = 20.sp
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    MedPriorityButton(
                        text = "Register First Vehicle",
                        onClick = { showAddDialog = true },
                        modifier = Modifier.width(220.dp)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (generalError != null) {
                        item {
                            Text(
                                text = generalError!!,
                                color = EmergencyRed,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                    }

                    items(vehicles, key = { it.id }) { vehicle ->
                        MedPriorityCard {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = vehicle.vehicleNumber,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Owner: ${vehicle.ownerName}",
                                        fontSize = 13.sp,
                                        color = TextSecondary
                                    )
                                }
                                StatusBadge(
                                    text = if (vehicle.isVerified) "Verified" else "Pending Review",
                                    isPositive = vehicle.isVerified
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(BackgroundLight, RoundedCornerShape(10.dp))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Type: ${vehicle.vehicleType}",
                                    fontSize = 13.sp,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Color: ${vehicle.vehicleColor}",
                                    fontSize = 13.sp,
                                    color = TextSecondary
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = { editingVehicle = vehicle },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text("Edit", color = AccentBlue, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                TextButton(
                                    onClick = { deletingVehicle = vehicle },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text("Delete", color = EmergencyRed, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialog: Add Vehicle
    if (showAddDialog) {
        var vehicleNumber by remember { mutableStateOf("") }
        var ownerName by remember { mutableStateOf("") }
        var vehicleType by remember { mutableStateOf("CAR") }
        var vehicleColor by remember { mutableStateOf("") }
        var formError by remember { mutableStateOf<String?>(null) }

        val vehicleTypes = listOf("CAR", "BIKE", "SUV", "VAN", "TRUCK", "OTHER")

        AlertDialog(
            onDismissRequest = { if (!isSubmitting) showAddDialog = false },
            title = { Text("Register New Vehicle", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    MedPriorityTextField(
                        value = vehicleNumber,
                        onValueChange = { vehicleNumber = it.uppercase() },
                        label = "Vehicle Number (e.g. DL01AB1234)"
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    MedPriorityTextField(
                        value = ownerName,
                        onValueChange = { ownerName = it },
                        label = "Owner Name"
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    MedPriorityTextField(
                        value = vehicleColor,
                        onValueChange = { vehicleColor = it },
                        label = "Vehicle Color (e.g. White)"
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Vehicle Type", fontSize = 12.sp, color = TextSecondary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        vehicleTypes.take(3).forEach { type ->
                            FilterChip(
                                selected = vehicleType == type,
                                onClick = { vehicleType = type },
                                label = { Text(type, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AccentBlue,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        vehicleTypes.drop(3).forEach { type ->
                            FilterChip(
                                selected = vehicleType == type,
                                onClick = { vehicleType = type },
                                label = { Text(type, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AccentBlue,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }

                    if (formError != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(text = formError!!, color = EmergencyRed, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !isSubmitting,
                    onClick = {
                        val num = vehicleNumber.trim().uppercase()
                        val owner = ownerName.trim()
                        val color = vehicleColor.trim()

                        if (num.length < 3) {
                            formError = "Enter a valid vehicle number (min 3 chars)"
                            return@TextButton
                        }
                        if (owner.length < 2) {
                            formError = "Enter owner name (min 2 chars)"
                            return@TextButton
                        }
                        if (color.length < 2) {
                            formError = "Enter vehicle color"
                            return@TextButton
                        }

                        isSubmitting = true
                        formError = null
                        coroutineScope.launch {
                            try {
                                val req = VehicleCreateRequest(
                                    vehicleNumber = num,
                                    ownerName = owner,
                                    vehicleType = vehicleType,
                                    vehicleColor = color
                                )
                                val res = apiService.createVehicle(req)
                                if (res.isSuccessful) {
                                    showAddDialog = false
                                    loadVehicles()
                                } else {
                                    val errBody = res.errorBody()?.string()
                                    val msg = try {
                                        if (errBody != null) JSONObject(errBody).optString("detail", "Vehicle registration failed.")
                                        else "Failed (${res.code()})"
                                    } catch (_: Exception) {
                                        "Failed (${res.code()})"
                                    }
                                    formError = msg
                                }
                            } catch (e: Exception) {
                                formError = "Network error. Please try again."
                            } finally {
                                isSubmitting = false
                            }
                        }
                    }
                ) {
                    Text(if (isSubmitting) "Adding..." else "Add Vehicle", color = AccentBlue, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(enabled = !isSubmitting, onClick = { showAddDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = SurfaceCard,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Dialog: Edit Vehicle
    if (editingVehicle != null) {
        val target = editingVehicle!!
        var ownerName by remember { mutableStateOf(target.ownerName) }
        var vehicleType by remember { mutableStateOf(target.vehicleType) }
        var vehicleColor by remember { mutableStateOf(target.vehicleColor) }
        var editError by remember { mutableStateOf<String?>(null) }

        val vehicleTypes = listOf("CAR", "BIKE", "SUV", "VAN", "TRUCK", "OTHER")

        AlertDialog(
            onDismissRequest = { if (!isSubmitting) editingVehicle = null },
            title = { Text("Edit Vehicle Details", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Vehicle: ${target.vehicleNumber} (Immutable)",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    MedPriorityTextField(
                        value = ownerName,
                        onValueChange = { ownerName = it },
                        label = "Owner Name"
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    MedPriorityTextField(
                        value = vehicleColor,
                        onValueChange = { vehicleColor = it },
                        label = "Vehicle Color"
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Vehicle Type", fontSize = 12.sp, color = TextSecondary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        vehicleTypes.take(3).forEach { type ->
                            FilterChip(
                                selected = vehicleType == type,
                                onClick = { vehicleType = type },
                                label = { Text(type, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AccentBlue,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        vehicleTypes.drop(3).forEach { type ->
                            FilterChip(
                                selected = vehicleType == type,
                                onClick = { vehicleType = type },
                                label = { Text(type, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AccentBlue,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }

                    if (editError != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(text = editError!!, color = EmergencyRed, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !isSubmitting,
                    onClick = {
                        isSubmitting = true
                        editError = null
                        coroutineScope.launch {
                            try {
                                val req = VehicleUpdateRequest(
                                    ownerName = ownerName.trim(),
                                    vehicleType = vehicleType,
                                    vehicleColor = vehicleColor.trim()
                                )
                                val res = apiService.updateVehicle(target.id, req)
                                if (res.isSuccessful) {
                                    editingVehicle = null
                                    loadVehicles()
                                } else {
                                    editError = "Update failed (${res.code()})"
                                }
                            } catch (e: Exception) {
                                editError = "Network error. Please try again."
                            } finally {
                                isSubmitting = false
                            }
                        }
                    }
                ) {
                    Text(if (isSubmitting) "Saving..." else "Save Changes", color = AccentBlue, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(enabled = !isSubmitting, onClick = { editingVehicle = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = SurfaceCard,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Dialog: Confirm Delete Vehicle
    if (deletingVehicle != null) {
        val target = deletingVehicle!!
        AlertDialog(
            onDismissRequest = { if (!isSubmitting) deletingVehicle = null },
            title = { Text("Delete Vehicle", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Are you sure you want to delete vehicle ${target.vehicleNumber}? This action cannot be undone.",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !isSubmitting,
                    onClick = {
                        isSubmitting = true
                        coroutineScope.launch {
                            try {
                                val res = apiService.deleteVehicle(target.id)
                                if (res.isSuccessful) {
                                    deletingVehicle = null
                                    loadVehicles()
                                } else {
                                    generalError = "Could not delete vehicle (${res.code()})"
                                }
                            } catch (e: Exception) {
                                generalError = "Network error during vehicle deletion."
                            } finally {
                                isSubmitting = false
                            }
                        }
                    }
                ) {
                    Text(if (isSubmitting) "Deleting..." else "Delete", color = EmergencyRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(enabled = !isSubmitting, onClick = { deletingVehicle = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = SurfaceCard,
            shape = RoundedCornerShape(16.dp)
        )
    }
}
