package pt.isec.amov.safetysec

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import pt.isec.amov.safetysec.ui.screens.account.LoginScreen
import pt.isec.amov.safetysec.ui.screens.account.RegisterScreen
import pt.isec.amov.safetysec.ui.screens.account.EditProfileScreen
import pt.isec.amov.safetysec.ui.screens.dashboard.monitor.AlertHistoryScreen
import pt.isec.amov.safetysec.ui.screens.dashboard.HomeScreen
import pt.isec.amov.safetysec.ui.screens.dashboard.monitor.MonitorDashboardScreen
import pt.isec.amov.safetysec.ui.screens.dashboard.protected.ProtectedDashboardScreen
import pt.isec.amov.safetysec.ui.screens.dashboard.protected.ManageRulesScreen
import pt.isec.amov.safetysec.ui.screens.dashboard.protected.MyMonitorsScreen

import pt.isec.amov.safetysec.ui.viewmodels.AuthViewModel
import pt.isec.amov.safetysec.utils.LanguageUtils
import androidx.core.content.edit
import com.google.firebase.auth.FirebaseAuth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val currentLang = LanguageUtils.currentCode
            val context = LocalContext.current
            val activityResultRegistryOwner = this@MainActivity
            val localizedContext = remember(currentLang, context) { LanguageUtils.getLocalizedContext(context) }

            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalActivityResultRegistryOwner provides activityResultRegistryOwner
            ) {
                val navController = rememberNavController()
                val authViewModel: AuthViewModel = viewModel()

                LaunchedEffect(Unit) {
                    val user = authViewModel.getLoggedInUser()

                    val prefs = context.getSharedPreferences("auth_prefs", MODE_PRIVATE)
                    val isMfaPending = prefs.getBoolean("mfa_pending", false)

                    if (user != null) {
                        if (isMfaPending) {
                            authViewModel.logout()
                            prefs.edit { putBoolean("mfa_pending", false) }
                        } else {
                            navController.navigate("home") { popUpTo("login") { inclusive = true } }
                        }
                    }

                    val dest = intent.getStringExtra("NAVIGATE_TO")
                    if (dest == "monitorDashboard") {
                        if (FirebaseAuth.getInstance().currentUser != null) {
                            navController.navigate("monitorDashboard")
                        }
                    }
                }

                NavHost(navController = navController, startDestination = "login") {
                    composable("login") { LoginScreen(navController = navController, viewModel = authViewModel) }
                    composable("register") { RegisterScreen(viewModel = authViewModel, navController = navController) }
                    composable("home") { HomeScreen(authViewModel = authViewModel, navController = navController) }
                    composable("monitorDashboard") { MonitorDashboardScreen(navController = navController) }
                    composable("protectedDashboard") { ProtectedDashboardScreen(navController = navController) }
                    composable("editProfile") { EditProfileScreen(navController = navController) }

                    composable("manageRules/{userId}/{monitorId}") { backStackEntry ->
                        val userId = backStackEntry.arguments?.getString("userId") ?: ""
                        val monitorId = backStackEntry.arguments?.getString("monitorId") ?: ""
                        ManageRulesScreen(navController = navController, userId = userId, monitorId = monitorId)
                    }

                    composable("manageRules/{userId}") { backStackEntry ->
                        val userId = backStackEntry.arguments?.getString("userId") ?: ""
                        val currentUserId = authViewModel.getLoggedInUser()?.uid ?: ""
                        ManageRulesScreen(navController = navController, userId = userId, monitorId = currentUserId)
                    }

                    composable("alertHistory") { AlertHistoryScreen(navController = navController) }
                    composable("myMonitors") { MyMonitorsScreen(navController = navController) }
                }
            }
        }
    }
}