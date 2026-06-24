package com.ona.miciclo.core.navigation

import kotlinx.serialization.Serializable

/**
 * Destinos de navegación type-safe usando Kotlin Serialization.
 * Cada objeto/clase representa una ruta única en la app.
 */

// ── Auth ──
@Serializable data object Login
@Serializable data object Register
@Serializable data object ForgotPassword

// ── Onboarding ──
@Serializable data object Onboarding
@Serializable data object CycleSetup

// ── Main (con bottom nav) ──
@Serializable data object Calendar
@Serializable data class DailyLogRoute(val date: String)
@Serializable data object History
@Serializable data object Settings
