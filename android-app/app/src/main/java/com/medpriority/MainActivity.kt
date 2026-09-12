package com.medpriority

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.medpriority.ui.EmergencyScreen
import com.medpriority.ui.LoginScreen
import com.medpriority.utils.TokenManager
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val tokenManager = TokenManager(this)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val startDestination = if (tokenManager.getToken() != null) {
                        // TODO: REAL FLOW - App should query /api/v1/emergencies/active to find if an SOS is active
                        // If none is active, go to a Dashboard where user can POST /api/v1/emergencies/start
                        "emergency/placeholder" 
                    } else {
                        "login"
                    }

                    NavHost(navController = navController, startDestination = startDestination) {
                        composable("login") {
                            LoginScreen(
                                tokenManager = tokenManager,
                                onLoginSuccess = {
                                    // TODO: REAL FLOW:
                                    // Login -> Dashboard -> POST /start -> Get real ID -> Navigate to emergency/{real_id}
                                    // Using a development placeholder here to not assume ID 1 is a real emergency.
                                    navController.navigate("emergency/placeholder") {
                                        popUpTo("login") { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable("emergency/{id}") { backStackEntry ->
                            // Development placeholder parsing
                            val idParam = backStackEntry.arguments?.getString("id")
                            val emergencyId = idParam?.toIntOrNull() ?: -1 // -1 signifies development placeholder
                            EmergencyScreen(emergencyId = emergencyId)
                        }
                    }
                }
            }
        }
    }
}
