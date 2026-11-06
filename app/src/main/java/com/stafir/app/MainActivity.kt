package com.stafir.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.stafir.app.system.Permissions
import com.stafir.app.ui.GalleryViewModel
import com.stafir.app.ui.home.HomeScreen
import com.stafir.app.ui.onboarding.OnboardingScreen
import com.stafir.app.ui.settings.BatteryGuideScreen
import com.stafir.app.ui.settings.QualityScreen
import com.stafir.app.ui.settings.SettingsScreen
import com.stafir.app.ui.settings.StatsScreen
import com.stafir.app.ui.theme.StafirTheme
import com.stafir.app.ui.viewer.ExternalViewerScreen
import com.stafir.app.ui.viewer.ViewerScreen

class MainActivity : ComponentActivity() {

    private val gallery: GalleryViewModel by viewModels()
    /** File aperto da un'altra app ("Apri con Stafir"). */
    private var externalUri by mutableStateOf<Pair<Uri, String?>?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        val app = application as StafirApp

        setContent {
            val s by app.container.settings.values.collectAsStateWithLifecycle()
            StafirTheme(themeMode = s.themeMode) {
                val ext = externalUri
                if (ext != null) {
                    ExternalViewerScreen(uri = ext.first, mimeType = ext.second, onClose = {
                        externalUri = null
                        if (!s.onboardingDone || !Permissions.hasMediaAccess(this)) finish()
                    })
                    return@StafirTheme
                }
                val nav = rememberNavController()
                // Calcolato una volta sola: cambiare la destinazione iniziale ricreerebbe la navigazione.
                val start = remember { if (s.onboardingDone && Permissions.hasMediaAccess(this)) "home" else "onboarding" }
                NavHost(navController = nav, startDestination = start) {
                    composable("onboarding") {
                        OnboardingScreen(onFinished = {
                            nav.navigate("home") { popUpTo("onboarding") { inclusive = true } }
                        })
                    }
                    composable("home") {
                        HomeScreen(
                            vm = gallery,
                            onOpen = { index -> nav.navigate("viewer/$index") },
                            onSettings = { nav.navigate("settings") },
                            onStats = { nav.navigate("stats") },
                            onBatteryGuide = { nav.navigate("battery") },
                        )
                    }
                    composable(
                        "viewer/{index}",
                        arguments = listOf(navArgument("index") { type = NavType.IntType }),
                    ) { entry ->
                        ViewerScreen(
                            vm = gallery,
                            startIndex = entry.arguments?.getInt("index") ?: 0,
                            onBack = { nav.popBackStack() },
                        )
                    }
                    composable("settings") {
                        SettingsScreen(
                            vm = gallery,
                            onBack = { nav.popBackStack() },
                            onStats = { nav.navigate("stats") },
                            onBatteryGuide = { nav.navigate("battery") },
                            onQuality = { nav.navigate("quality") },
                        )
                    }
                    composable("quality") { QualityScreen(onBack = { nav.popBackStack() }) }
                    composable("stats") { StatsScreen(vm = gallery, onBack = { nav.popBackStack() }) }
                    composable("battery") { BatteryGuideScreen(vm = gallery, onBack = { nav.popBackStack() }) }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        if (Permissions.hasMediaAccess(this)) gallery.refresh()
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.action == Intent.ACTION_VIEW || intent.action == "com.android.camera.action.REVIEW") {
            intent.data?.let { externalUri = it to intent.type }
        }
    }
}
