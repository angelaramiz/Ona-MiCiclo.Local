package com.ona.miciclo

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.ona.miciclo.auth.presentation.AuthViewModel
import com.ona.miciclo.auth.presentation.ForgotPasswordScreen
import com.ona.miciclo.auth.presentation.LoginScreen
import com.ona.miciclo.auth.presentation.RegisterScreen
import com.ona.miciclo.calendar.presentation.CalendarScreen
import com.ona.miciclo.calendar.presentation.CalendarViewModel
import com.ona.miciclo.calendar.presentation.DailyLogScreen
import com.ona.miciclo.core.navigation.*
import com.ona.miciclo.core.ui.theme.OnaMiCicloTheme
import com.ona.miciclo.history.presentation.HistoryScreen
import com.ona.miciclo.history.presentation.HistoryViewModel
import com.ona.miciclo.onboarding.presentation.CycleSetupScreen
import com.ona.miciclo.onboarding.presentation.OnboardingScreen
import com.ona.miciclo.onboarding.presentation.OnboardingViewModel
import com.ona.miciclo.settings.presentation.SettingsScreen
import com.ona.miciclo.settings.presentation.SettingsViewModel
import javax.inject.Inject
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.firstOrNull
import dagger.hilt.android.AndroidEntryPoint
import com.ona.miciclo.data.local.entity.UserPreferencesEntity
import com.ona.miciclo.dashboard.presentation.DashboardScreen
import com.ona.miciclo.dashboard.presentation.DashboardViewModel

/**
 * Single Activity entry point.
 * Usa Jetpack Navigation Compose con rutas type-safe.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var syncManager: com.ona.miciclo.core.sync.SupabaseSyncManager

    @Inject
    lateinit var userPreferencesDao: com.ona.miciclo.data.local.dao.UserPreferencesDao

    @Inject
    lateinit var authRepository: com.ona.miciclo.auth.domain.repository.AuthRepository

    // Lanzador para solicitar permiso de notificaciones
    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // El usuario puede aceptar o denegar, no necesitamos hacer nada específico aquí
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Solicitar permiso de notificaciones en Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        lifecycleScope.launch {
            authRepository.currentUser.collect { user ->
                if (user != null) {
                    val prefs = userPreferencesDao.getByUserId(user.uid)
                    if (prefs?.userRole == "partner" && !prefs.linkedUserId.isNullOrEmpty()) {
                        syncManager.startPartnerSyncListener(user.uid, prefs.linkedUserId)
                    } else {
                        // Sincronizar datos propios (descargar de la nube si existen y subir cambios locales)
                        syncManager.downloadUserDataFromCloud(user.uid)
                        syncManager.startHostessAutoSync(user.uid)
                    }
                } else {
                    syncManager.stopAllSync()
                }
            }
        }

        setContent {
            OnaMiCicloTheme {
                OnaNavigation(
                    userPreferencesDao = userPreferencesDao,
                    authRepository = authRepository
                )
            }
        }
    }
}

@Composable
fun OnaNavigation(
    userPreferencesDao: com.ona.miciclo.data.local.dao.UserPreferencesDao,
    authRepository: com.ona.miciclo.auth.domain.repository.AuthRepository
) {
    val navController = rememberNavController()
    val authViewModel: AuthViewModel = hiltViewModel()
    val isAuthenticated = authViewModel.isAuthenticated

    // Determinar destino inicial
    val startDestination: Any = if (isAuthenticated) Dashboard else Login

    val authUser by authRepository.currentUser.collectAsState()
    val userId = authUser?.uid ?: ""
    val prefs: UserPreferencesEntity? by userPreferencesDao.observeByUserId(userId).collectAsState(initial = null)
    val isPartner = prefs?.userRole == "partner"

    // Pantallas que muestran bottom nav
    val bottomNavRoutes = if (isPartner) {
        listOf(Dashboard, Calendar, Settings)
    } else {
        listOf(Dashboard, Calendar, History, Settings)
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val showBottomBar = navBackStackEntry?.destination?.let { destination ->
        bottomNavRoutes.any { route ->
            when (route) {
                Dashboard -> destination.hasRoute<Dashboard>()
                Calendar -> destination.hasRoute<Calendar>()
                History -> destination.hasRoute<History>()
                Settings -> destination.hasRoute<Settings>()
                else -> false
            }
        }
    } ?: false

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavRoutes.forEach { route ->
                        val (icon, label, selected) = when (route) {
                            Dashboard -> Triple(Icons.Default.Home, "Dashboard", navBackStackEntry?.destination?.hasRoute<Dashboard>() == true)
                            Calendar -> Triple(Icons.Default.CalendarMonth, "Calendario", navBackStackEntry?.destination?.hasRoute<Calendar>() == true)
                            History -> Triple(Icons.Default.History, "Historial", navBackStackEntry?.destination?.hasRoute<History>() == true)
                            Settings -> Triple(Icons.Default.Settings, "Config", navBackStackEntry?.destination?.hasRoute<Settings>() == true)
                            else -> Triple(Icons.Default.Home, "Inicio", false)
                        }
                        NavigationBarItem(
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label) },
                            selected = selected,
                            onClick = {
                                navController.navigate(route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding)
        ) {
            // ── Auth ──
            composable<Login> {
                val viewModel: AuthViewModel = hiltViewModel()
                LoginScreen(
                    viewModel = viewModel,
                    onNavigateToRegister = { navController.navigate(Register) },
                    onNavigateToForgotPassword = { navController.navigate(ForgotPassword) },
                    onLoginSuccess = {
                        navController.navigate(Onboarding) {
                            popUpTo(Login) { inclusive = true }
                        }
                    }
                )
            }

            composable<Register> {
                val viewModel: AuthViewModel = hiltViewModel()
                RegisterScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onRegisterSuccess = {
                        navController.navigate(Onboarding) {
                            popUpTo(Login) { inclusive = true }
                        }
                    }
                )
            }

            composable<ForgotPassword> {
                val viewModel: AuthViewModel = hiltViewModel()
                ForgotPasswordScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // ── Onboarding ──
            composable<Onboarding> {
                OnboardingScreen(
                    onNavigateToCycleSetup = {
                        navController.navigate(CycleSetup) {
                            popUpTo(Onboarding) { inclusive = true }
                        }
                    }
                )
            }

            composable<CycleSetup> {
                val viewModel: OnboardingViewModel = hiltViewModel()
                CycleSetupScreen(
                    viewModel = viewModel,
                    onSetupComplete = {
                        navController.navigate(Dashboard) {
                            popUpTo(CycleSetup) { inclusive = true }
                        }
                    }
                )
            }

            // ── Main screens ──
            composable<Dashboard> {
                val viewModel: DashboardViewModel = hiltViewModel()
                DashboardScreen(viewModel = viewModel)
            }

            composable<Calendar> {
                val viewModel: CalendarViewModel = hiltViewModel()
                CalendarScreen(
                    viewModel = viewModel,
                    onNavigateToDailyLog = { date ->
                        navController.navigate(DailyLogRoute(date = date))
                    }
                )
            }

            composable<DailyLogRoute> { backStackEntry ->
                val route = backStackEntry.toRoute<DailyLogRoute>()
                val viewModel: CalendarViewModel = hiltViewModel()
                DailyLogScreen(
                    viewModel = viewModel,
                    dateString = route.date,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable<History> {
                val viewModel: HistoryViewModel = hiltViewModel()
                HistoryScreen(viewModel = viewModel)
            }

            composable<Settings> {
                val viewModel: SettingsViewModel = hiltViewModel()
                SettingsScreen(
                    viewModel = viewModel,
                    onSignedOut = {
                        navController.navigate(Login) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }
        }
    }
}
