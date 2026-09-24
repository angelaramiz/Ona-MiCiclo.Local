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
import androidx.compose.runtime.rememberCoroutineScope
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
import com.ona.miciclo.core.sync.SyncScheduler
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // #region debug-point A:activity-oncreate
        com.ona.miciclo.core.debug.DebugTelemetry.emit(
            hypothesisId = "A",
            location = "MainActivity:onCreate",
            msg = "[DEBUG] MainActivity iniciada",
            data = org.json.JSONObject().put("sdk", Build.VERSION.SDK_INT)
        )
        // #endregion

        lifecycleScope.launch {
            authRepository.currentUser.collect { user ->
                if (user != null) {
                    // Restaurar rol/vínculo desde la nube si este dispositivo no los
                    // tiene (cambio de móvil).
                    restoreRoleFromCloudIfMissing(user.uid, userPreferencesDao, syncManager)
                    var prefs = userPreferencesDao.getByUserId(user.uid)
                    // #region debug-point C:startup-user-state
                    com.ona.miciclo.core.debug.DebugTelemetry.emit(
                        hypothesisId = "C",
                        location = "MainActivity:authCollect",
                        msg = "[DEBUG] Estado persistido al arrancar",
                        data = org.json.JSONObject()
                            .put("uid", user.uid)
                            .put("role", prefs?.userRole ?: "null")
                            .put("linkedUserId", prefs?.linkedUserId ?: "null")
                    )
                    // #endregion
                    if (prefs?.userRole == "partner" && !prefs.linkedUserId.isNullOrEmpty()) {
                        syncManager.startPartnerSyncListener(user.uid, prefs.linkedUserId)
                    } else {
                        // Sincronizar datos propios (descargar de la nube si existen y subir cambios locales)
                        syncManager.downloadUserDataFromCloud(user.uid)
                        syncManager.startHostessAutoSync(user.uid)
                    }
                    // Sync periódico en segundo plano (WorkManager): mantiene la
                    // comunicación hostess<->partner aunque la app esté cerrada.
                    SyncScheduler.schedulePeriodic(this@MainActivity, user.uid)
                } else {
                    syncManager.stopAllSync()
                    SyncScheduler.cancelAll(this@MainActivity)
                }
            }
        }

        setContent {
            OnaMiCicloTheme {
                OnaNavigation(
                    userPreferencesDao = userPreferencesDao,
                    authRepository = authRepository,
                    syncManager = syncManager
                )
            }
        }
    }
}

/**
 * Restaura rol/vínculo desde la nube si este dispositivo no los tiene
 * (cambio de móvil). Sin esto, un partner en un móvil nuevo aparece como
 * hostess y el sync no carga. Marca onboarding como completado porque es
 * un usuario que regresa (evita repetir el onboarding).
 */
private suspend fun restoreRoleFromCloudIfMissing(
    userId: String,
    userPreferencesDao: com.ona.miciclo.data.local.dao.UserPreferencesDao,
    syncManager: com.ona.miciclo.core.sync.SupabaseSyncManager
) {
    val prefs = userPreferencesDao.getByUserId(userId)
    val hasRole = !prefs?.userRole.isNullOrEmpty() &&
        !(prefs?.userRole == "partner" && prefs?.linkedUserId.isNullOrEmpty())
    if (hasRole) return
    syncManager.fetchCloudRole(userId)?.let { (role, linkedId) ->
        val base = prefs ?: com.ona.miciclo.data.local.entity.UserPreferencesEntity(userId = userId)
        userPreferencesDao.insertOrUpdate(
            base.copy(userRole = role, linkedUserId = linkedId, onboardingCompletado = true)
        )
    }
}

@Composable
fun OnaNavigation(
    userPreferencesDao: com.ona.miciclo.data.local.dao.UserPreferencesDao,
    authRepository: com.ona.miciclo.auth.domain.repository.AuthRepository,
    syncManager: com.ona.miciclo.core.sync.SupabaseSyncManager
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
                val scope = rememberCoroutineScope()
                LoginScreen(
                    viewModel = viewModel,
                    onNavigateToRegister = { navController.navigate(Register) },
                    onNavigateToForgotPassword = { navController.navigate(ForgotPassword) },
                    onLoginSuccess = {
                        // Restaurar rol/vínculo ANTES del gate (cambio de móvil),
                        // luego decidir destino. Sin esto hay carrera y va a Onboarding.
                        scope.launch {
                            val uid = authRepository.currentUser.value?.uid ?: ""
                            restoreRoleFromCloudIfMissing(uid, userPreferencesDao, syncManager)
                            val completed = userPreferencesDao.getByUserId(uid)?.onboardingCompletado == true
                            if (completed) {
                                navController.navigate(Dashboard) {
                                    popUpTo(Login) { inclusive = true }
                                }
                            } else {
                                navController.navigate(Onboarding) {
                                    popUpTo(Login) { inclusive = true }
                                }
                            }
                        }
                    }
                )
            }

            composable<Register> {
                val viewModel: AuthViewModel = hiltViewModel()
                val scope = rememberCoroutineScope()
                RegisterScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onRegisterSuccess = {
                        // Un registro siempre es usuario nuevo → onboarding.
                        scope.launch {
                            navController.navigate(Onboarding) {
                                popUpTo(Login) { inclusive = true }
                            }
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
                HistoryScreen(
                    viewModel = viewModel,
                    onNavigateToCalendar = {
                        navController.navigate(Calendar) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
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
