package it.fast4x.opentv.ui.navigation

import android.net.Uri
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.media3.common.util.UnstableApi
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import it.fast4x.opentv.ui.screens.guide.GuideScreen
import it.fast4x.opentv.ui.screens.home.HomeScreen
import it.fast4x.opentv.ui.screens.player.PlayerScreen
import it.fast4x.opentv.ui.screens.settings.SettingsScreen

/** Definizione delle rotte di navigazione. */
object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val GUIDE = "guide"

    const val PLAYER = "player/{channelId}"

    fun player(channelId: String): String =
        "player/${Uri.encode(channelId, "")}"
}

/** Grafo di navigazione principale dell'app. */
@UnstableApi
@ExperimentalMaterial3Api
@Composable
fun OpenTvNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onOpenChannel = { channelId ->
                    navController.navigate(Routes.player(channelId))
                },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenGuide = { navController.navigate(Routes.GUIDE) },
            )
        }

        composable(Routes.GUIDE) {
            GuideScreen(
                onBack = { navController.popBackStack() },
                onOpenChannel = { channelId ->
                    navController.navigate(Routes.player(channelId))
                },
            )
        }

        composable(Routes.PLAYER) { backStackEntry ->
            val channelId = backStackEntry.arguments?.getString("channelId").orEmpty()
            PlayerScreen(
                channelId = channelId,
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
