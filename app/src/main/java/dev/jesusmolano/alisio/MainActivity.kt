package dev.jesusmolano.alisio

import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.jesusmolano.alisio.feature.home.AppListScreen
import dev.jesusmolano.alisio.feature.home.HomeViewModel
import dev.jesusmolano.alisio.feature.onboarding.OnboardingScreen
import dev.jesusmolano.alisio.ui.theme.AlisioTheme

class MainActivity : ComponentActivity() {
    private val homeViewModel: HomeViewModel by viewModels {
        HomeViewModel.factory((application as AlisioApp).container.appsRepository)
    }

    private var isDefaultHome by mutableStateOf(false)
    private var onboardingSkipped by mutableStateOf(false)
    private var homePresses by mutableIntStateOf(0)

    // El resultado no importa: onResume vuelve a comprobar el rol.
    private val requestHomeRole = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        val transparent = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = transparent, navigationBarStyle = transparent)
        super.onCreate(savedInstanceState)
        isDefaultHome = holdsHomeRole()

        setContent {
            AlisioTheme {
                // Atrás en el inicio no hace nada.
                BackHandler {}
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    if (isDefaultHome || onboardingSkipped) {
                        AppListScreen(viewModel = homeViewModel, homePresses = homePresses)
                    } else {
                        OnboardingScreen(
                            onSetDefault = ::requestHomeRole,
                            onSkip = { onboardingSkipped = true }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isDefaultHome = holdsHomeRole()
    }

    /** Pulsar «inicio» estando ya en Alisio llega aquí: se vuelve al estado inicial. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            homePresses++
        }
    }

    private fun holdsHomeRole(): Boolean = getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_HOME)

    private fun requestHomeRole() {
        val roleManager = getSystemService(RoleManager::class.java)
        try {
            if (roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                requestHomeRole.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME))
            } else {
                startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
            }
        } catch (_: ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }
}
