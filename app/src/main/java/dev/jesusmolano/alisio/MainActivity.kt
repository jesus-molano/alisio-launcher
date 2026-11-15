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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.jesusmolano.alisio.feature.home.HomeScreen
import dev.jesusmolano.alisio.feature.home.HomeViewModel
import dev.jesusmolano.alisio.feature.onboarding.OnboardingScreen
import dev.jesusmolano.alisio.feature.search.SearchScreen
import dev.jesusmolano.alisio.feature.search.SearchViewModel
import dev.jesusmolano.alisio.ui.theme.AlisioTheme

class MainActivity : ComponentActivity() {
    private val homeViewModel: HomeViewModel by viewModels {
        HomeViewModel.factory((application as AlisioApp).container)
    }
    private val searchViewModel: SearchViewModel by viewModels {
        SearchViewModel.factory((application as AlisioApp).container, applicationContext)
    }

    private var isDefaultHome by mutableStateOf(false)
    private var onboardingSkipped by mutableStateOf(false)
    private var searchOpen by mutableStateOf(false)
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
                // Atrás cierra la barra; en el Inicio no hace nada.
                BackHandler { searchOpen = false }
                if (isDefaultHome || onboardingSkipped) {
                    Box(Modifier.fillMaxSize()) {
                        HomeScreen(
                            viewModel = homeViewModel,
                            homePresses = homePresses,
                            searchOpen = searchOpen,
                            onOpenSearch = { searchOpen = true }
                        )
                        // One UI no anima el gesto atrás en apps de terceros (§9): la barra trae su animación.
                        AnimatedVisibility(
                            visible = searchOpen,
                            enter = fadeIn() + slideInVertically { it / 8 },
                            exit = fadeOut() + slideOutVertically { it / 8 }
                        ) {
                            SearchScreen(viewModel = searchViewModel, onClose = { searchOpen = false })
                        }
                    }
                } else {
                    // El onboarding va sobre fondo opaco: la ventana deja ver el fondo de pantalla.
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
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
        val holdsRole = holdsHomeRole()
        if (holdsRole && !isDefaultHome) homeViewModel.onHomeRoleGranted()
        isDefaultHome = holdsRole
    }

    override fun onStop() {
        super.onStop()
        // Al volver de otra app el Inicio aparece limpio, sin la barra a medias.
        searchOpen = false
    }

    /** Pulsar «inicio» estando ya en Alisio llega aquí: se cierran la barra y las hojas. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            searchOpen = false
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
