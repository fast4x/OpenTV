package it.fast4x.opentv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.ExperimentalMaterial3Api
import it.fast4x.opentv.ui.navigation.OpenTvNavHost
import it.fast4x.opentv.ui.theme.OpenTvTheme

/**
 * Activity unica per telefono e Android TV: la UI Compose adatta
 * automaticamente layout e focus in base al dispositivo.
 */
class MainActivity : ComponentActivity() {

    @ExperimentalMaterial3Api
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OpenTvTheme {
                OpenTvNavHost()
            }
        }
    }
}
