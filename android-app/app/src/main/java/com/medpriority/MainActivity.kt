package com.medpriority

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.medpriority.ui.*
import com.medpriority.ui.theme.BackgroundLight
import com.medpriority.ui.theme.MedPriorityTheme
import com.medpriority.utils.TokenManager

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val tokenManager = TokenManager.getInstance(this)

        setContent {
            MedPriorityTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = BackgroundLight
                ) {
                    val navController = rememberNavController()
                    val startDestination = if (tokenManager.getToken() != null) "home" else "login"

                    NavHost(
                        navController = navController,
                        startDestination = startDestination
                    ) {
                        // -----------------------------------------------------
                        // Authentication Screens
                        // -----------------------------------------------------
                        composable("login") {
                            LoginScreen(
                                tokenManager = tokenManager,
                                onLoginSuccess = {
                                    navController.navigate("home") {
                                        popUpTo("login") { inclusive = true }
                                    }
                                },
                                onNavigateToSignUp = {
                                    navController.navigate("signup")
                                }
                            )
                        }

                        composable("signup") {
                            SignUpScreen(
                                onNavigateToLogin = {
                                    navController.popBackStack()
                                }
                            )
                        }

                        // -----------------------------------------------------
                        // Authenticated Screens
                        // -----------------------------------------------------
                        composable("home") {
                            HomeScreen(
                                tokenManager = tokenManager,
                                onNavigateToVehicles = {
                                    navController.navigate("vehicles")
                                },
                                onNavigateToProfile = {
                                    navController.navigate("profile")
                                },
                                onNavigateToEmergency = { emergencyId ->
                                    navController.navigate("emergency/$emergencyId")
                                },
                                onLogout = {
                                    navController.navigate("login") {
                                        popUpTo(0) { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable("vehicles") {
                            VehiclesScreen(
                                onNavigateBack = {
                                    navController.popBackStack()
                                }
                            )
                        }

                        composable("profile") {
                            ProfileScreen(
                                tokenManager = tokenManager,
                                onNavigateBack = {
                                    navController.popBackStack()
                                },
                                onLogout = {
                                    navController.navigate("login") {
                                        popUpTo(0) { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable(
                            route = "emergency/{id}",
                            arguments = listOf(navArgument("id") { type = NavType.IntType })
                        ) { backStackEntry ->
                            val emergencyId = backStackEntry.arguments?.getInt("id") ?: -1
                            EmergencyScreen(
                                emergencyId = emergencyId,
                                onNavigateToHome = {
                                    navController.navigate("home") {
                                        popUpTo("home") { inclusive = true }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
