package com.hackastic.decmed.ui.navigation

/**
 * Sealed class defining all navigation routes in the app.
 * Using a sealed class instead of an enum because it allows
 * future extension with typed arguments (e.g., Screen.Detail(id)).
 */
sealed class Screen(val route: String) {
    object TermsOfService : Screen("terms_of_service")
    object PatientAuth : Screen("patient_auth")
    object PatientSignup : Screen("patient_signup")
    object PatientSignin : Screen("patient_signin")
    object PatientCompleteProfile : Screen("patient_complete_profile")
    object PatientUnlock : Screen("patient_unlock")
    object SensorList : Screen("sensor_list")
    object SensorConfig : Screen("sensor_config")
    object Home : Screen("home")
    object Settings : Screen("settings")
    object Data : Screen("data")
    object PghdCollection : Screen("pghd_collection")
}
