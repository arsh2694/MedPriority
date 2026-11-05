package com.medpriority.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medpriority.R
import com.medpriority.network.RetrofitClient
import com.medpriority.ui.components.MedPriorityButton
import com.medpriority.ui.components.MedPriorityTextField
import com.medpriority.ui.theme.*
import com.medpriority.utils.TokenManager
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable
fun LoginScreen(
    tokenManager: TokenManager,
    onLoginSuccess: () -> Unit,
    onNavigateToSignUp: () -> Unit
) {
    val context = LocalContext.current
    val apiService = remember { RetrofitClient.getApiService(context) }
    val coroutineScope = rememberCoroutineScope()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var emailError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var generalError by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    fun validate(): Boolean {
        var isValid = true
        emailError = null
        passwordError = null
        generalError = null

        val emailTrimmed = email.trim()
        if (emailTrimmed.isBlank()) {
            emailError = "Email is required"
            isValid = false
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(emailTrimmed).matches()) {
            emailError = "Please enter a valid email address"
            isValid = false
        }

        if (password.isBlank()) {
            passwordError = "Password is required"
            isValid = false
        }
        return isValid
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
                .padding(horizontal = 28.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // MedPriority Brand Logo
            Image(
                painter = painterResource(id = R.drawable.medpriority_logo),
                contentDescription = "MedPriority Logo",
                modifier = Modifier
                    .size(84.dp)
                    .clip(RoundedCornerShape(percent = 22)),
                contentScale = ContentScale.Fit
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Welcome Back",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                letterSpacing = (-0.5).sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Sign in to access emergency assistance and vehicle management",
                fontSize = 14.sp,
                color = TextSecondary,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(36.dp))

            MedPriorityTextField(
                value = email,
                onValueChange = {
                    email = it
                    emailError = null
                    generalError = null
                },
                label = "Email Address",
                errorMessage = emailError,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            MedPriorityTextField(
                value = password,
                onValueChange = {
                    password = it
                    passwordError = null
                    generalError = null
                },
                label = "Password",
                isPassword = true,
                errorMessage = passwordError,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                )
            )

            if (generalError != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = generalError!!,
                    color = EmergencyRed,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            MedPriorityButton(
                text = "Sign In",
                isLoading = isLoading,
                onClick = {
                    if (validate()) {
                        isLoading = true
                        generalError = null
                        coroutineScope.launch {
                            try {
                                val response = apiService.login(email.trim(), password)
                                if (response.isSuccessful && response.body() != null) {
                                    val token = response.body()!!.accessToken
                                    tokenManager.saveToken(token)
                                    onLoginSuccess()
                                } else {
                                    val errBody = response.errorBody()?.string()
                                    val msg = try {
                                        if (errBody != null) {
                                            JSONObject(errBody).optString("detail", "Invalid email or password.")
                                        } else "Invalid credentials."
                                    } catch (_: Exception) {
                                        "Invalid credentials."
                                    }
                                    generalError = msg
                                }
                            } catch (e: Exception) {
                                val currentTarget = RetrofitClient.activeHost ?: if (RetrofitClient.isRunningOnEmulator()) "10.0.2.2:8000" else "192.168.29.14:8000"
                                generalError = "Unable to connect to server ($currentTarget). Please check network or change server IP below."
                            } finally {
                                isLoading = false
                            }
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(28.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Don't have an account? ",
                    fontSize = 14.sp,
                    color = TextSecondary
                )
                TextButton(
                    onClick = onNavigateToSignUp,
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        text = "Sign Up",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AccentBlue
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Developer / Network Server IP Configuration
            var showServerDialog by remember { mutableStateOf(false) }
            val currentServerText = tokenManager.getServerUrl() ?: (RetrofitClient.activeHost ?: if (RetrofitClient.isRunningOnEmulator()) "10.0.2.2:8000" else "192.168.29.14:8000")

            TextButton(
                onClick = { showServerDialog = true },
                contentPadding = PaddingValues(4.dp)
            ) {
                Text(
                    text = "Server: $currentServerText ⚙",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }

            if (showServerDialog) {
                var serverInput by remember { mutableStateOf(currentServerText) }
                AlertDialog(
                    onDismissRequest = { showServerDialog = false },
                    title = { Text(text = "Server Configuration", fontWeight = FontWeight.Bold) },
                    text = {
                        Column {
                            Text(
                                text = "Set the backend host:port for your network:\n• Physical Device (Wi-Fi): 192.168.29.14:8000\n• Physical Device (USB Reverse): 127.0.0.1:8000\n• Emulator: 10.0.2.2:8000",
                                fontSize = 12.sp,
                                color = TextSecondary,
                                lineHeight = 16.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = serverInput,
                                onValueChange = { serverInput = it },
                                singleLine = true,
                                label = { Text("Server Host:Port") }
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                val clean = serverInput.trim().removePrefix("http://").removePrefix("https://").trimEnd('/')
                                tokenManager.setServerUrl(clean)
                                RetrofitClient.activeHost = clean
                                showServerDialog = false
                            }
                        ) {
                            Text("Save", fontWeight = FontWeight.Bold, color = AccentBlue)
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                tokenManager.setServerUrl(null)
                                RetrofitClient.activeHost = null
                                showServerDialog = false
                            }
                        ) {
                            Text("Reset Auto", color = TextSecondary)
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
