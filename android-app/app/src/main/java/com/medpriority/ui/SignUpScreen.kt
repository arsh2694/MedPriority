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
import com.medpriority.network.RegisterRequest
import com.medpriority.network.RetrofitClient
import com.medpriority.ui.components.MedPriorityButton
import com.medpriority.ui.components.MedPriorityTextField
import com.medpriority.ui.theme.*
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable
fun SignUpScreen(
    onNavigateToLogin: () -> Unit
) {
    val context = LocalContext.current
    val apiService = remember { RetrofitClient.getApiService(context) }
    val coroutineScope = rememberCoroutineScope()

    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    var fullNameError by remember { mutableStateOf<String?>(null) }
    var emailError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var confirmPasswordError by remember { mutableStateOf<String?>(null) }

    var generalError by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    fun validate(): Boolean {
        var isValid = true
        fullNameError = null
        emailError = null
        passwordError = null
        confirmPasswordError = null
        generalError = null

        if (fullName.trim().length < 2) {
            fullNameError = "Name must be at least 2 characters"
            isValid = false
        }
        val emailTrimmed = email.trim()
        if (emailTrimmed.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(emailTrimmed).matches()) {
            emailError = "Please enter a valid email address"
            isValid = false
        }
        if (password.length < 8) {
            passwordError = "Password must be at least 8 characters"
            isValid = false
        }
        if (password != confirmPassword) {
            confirmPasswordError = "Passwords do not match"
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
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // MedPriority Brand Logo
            Image(
                painter = painterResource(id = R.drawable.medpriority_logo),
                contentDescription = "MedPriority Logo",
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(percent = 22)),
                contentScale = ContentScale.Fit
            )

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "Create Account",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                letterSpacing = (-0.5).sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Register to enable emergency vehicle visibility",
                fontSize = 14.sp,
                color = TextSecondary,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            MedPriorityTextField(
                value = fullName,
                onValueChange = {
                    fullName = it
                    fullNameError = null
                },
                label = "Full Name",
                errorMessage = fullNameError,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            MedPriorityTextField(
                value = email,
                onValueChange = {
                    email = it
                    emailError = null
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
                },
                label = "Password (min 8 characters)",
                isPassword = true,
                errorMessage = passwordError,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Next
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            MedPriorityTextField(
                value = confirmPassword,
                onValueChange = {
                    confirmPassword = it
                    confirmPasswordError = null
                },
                label = "Confirm Password",
                isPassword = true,
                errorMessage = confirmPasswordError,
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
                    fontWeight = FontWeight.Medium
                )
            }

            if (successMessage != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = successMessage!!,
                    color = SuccessGreen,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            MedPriorityButton(
                text = "Create Account",
                isLoading = isLoading,
                onClick = {
                    if (validate()) {
                        isLoading = true
                        generalError = null
                        successMessage = null
                        coroutineScope.launch {
                            try {
                                val req = RegisterRequest(
                                    name = fullName.trim(),
                                    email = email.trim(),
                                    password = password
                                )
                                val response = apiService.register(req)
                                if (response.isSuccessful) {
                                    successMessage = "Account created successfully! Redirecting to login..."
                                    kotlinx.coroutines.delay(1200)
                                    onNavigateToLogin()
                                } else {
                                    val errBody = response.errorBody()?.string()
                                    val msg = try {
                                        if (errBody != null) {
                                            JSONObject(errBody).optString("detail", "Registration failed. Please try again.")
                                        } else "Registration failed (${response.code()})"
                                    } catch (_: Exception) {
                                        "Registration failed (${response.code()})"
                                    }
                                    generalError = msg
                                }
                            } catch (e: Exception) {
                                generalError = "Unable to reach server. Please check your connection."
                            } finally {
                                isLoading = false
                            }
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Already have an account? ",
                    fontSize = 14.sp,
                    color = TextSecondary
                )
                TextButton(
                    onClick = onNavigateToLogin,
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        text = "Sign In",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AccentBlue
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
