package io.github.diamondrubinny.scary

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.diamondrubinny.scary.audio.AmbientPlayer
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import io.github.diamondrubinny.scary.ui.ScaryRoot
import io.github.diamondrubinny.scary.ui.home.HomeViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: HomeViewModel by viewModels {
        HomeViewModel.factory((application as ScaryApplication).container)
    }

    private val ambient = AmbientPlayer()

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleOverride.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ScaryRoot(viewModel) }

        val settings = (application as ScaryApplication).container.settingsRepository
        lifecycleScope.launch {
            // Sound only while the launcher is visible; leaving for Stremio stops it.
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                settings.prefs.map { it.soundsEnabled }.distinctUntilChanged().collect { enabled ->
                    if (enabled) ambient.start() else ambient.stop()
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        ambient.stop()
        viewModel.onBackgrounded()
    }

    override fun onResume() {
        super.onResume()
        // Apps may have been installed or removed while another app was in front.
        viewModel.refreshApps()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) viewModel.onHomePressed()
    }
}
