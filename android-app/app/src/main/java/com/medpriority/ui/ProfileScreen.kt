package com.medpriority.ui

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medpriority.network.RetrofitClient
import com.medpriority.network.UserDto
import com.medpriority.ui.components.MedPriorityButton
import com.medpriority.ui.components.MedPriorityCard
import com.medpriority.ui.components.StatusBadge
import com.medpriority.ui.theme.*
import com.medpriority.utils.TokenManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    tokenManager: TokenManager,
    onNavigateBack: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val apiService = remember { RetrofitClient.getApiService(context) }
    val coroutineScope = rememberCoroutineScope()

    var user by remember { mutableStateOf<UserDto?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        coroutineScope.launch {
            try {
                val res = apiService.getCurrentUser()
                if (res.isSuccessful && res.body() != null) {
                    user = res.body()
                } else if (res.code() == 401) {
                    tokenManager.clearToken()
                    onLogout()
                }
            } catch (_: Exception) {
            } finally {
                isLoading = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Account Profile",
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
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AccentBlue)
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Avatar Circle
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .background(AccentBlueSubtle, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = (user?.name?.firstOrNull() ?: 'U').toString(),
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentBlue
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = user?.name ?: "Authenticated User",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = user?.email ?: "",
                        fontSize = 14.sp,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    // Account Information Card
                    MedPriorityCard {
                        Text(
                            text = "Account Information",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        ProfileRow(label = "User ID", value = "#${user?.id ?: "-"}")
                        Divider(color = BorderSubtle, modifier = Modifier.padding(vertical = 10.dp))
                        ProfileRow(label = "Role", value = user?.role ?: "USER")
                        Divider(color = BorderSubtle, modifier = Modifier.padding(vertical = 10.dp))
                        ProfileRow(
                            label = "Status",
                            value = if (user?.isActive == true) "Active" else "Inactive",
                            isStatus = true
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Security & Session Info Card
                    MedPriorityCard {
                        Text(
                            text = "Security & Storage",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        ProfileRow(label = "Token Storage", value = "EncryptedSharedPreferences")
                        Divider(color = BorderSubtle, modifier = Modifier.padding(vertical = 10.dp))
                        ProfileRow(label = "Cipher Algorithm", value = "AES-256-GCM")
                        Divider(color = BorderSubtle, modifier = Modifier.padding(vertical = 10.dp))
                        ProfileRow(label = "Client Version", value = "1.0.0 (Day 8)")
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    // Logout Button
                    MedPriorityButton(
                        text = "Sign Out",
                        containerColor = EmergencyRed,
                        contentColor = SurfaceCard,
                        onClick = { showLogoutDialog = true }
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Sign Out", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Are you sure you want to sign out of MedPriority?",
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
                    Text("Sign Out", color = EmergencyRed, fontWeight = FontWeight.Bold)
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

@Composable
private fun ProfileRow(
    label: String,
    value: String,
    isStatus: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 14.sp, color = TextSecondary)
        if (isStatus) {
            StatusBadge(text = value, isPositive = value == "Active")
        } else {
            Text(text = value, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
        }
    }
}
