package io.github.diamondrubinny.scary

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import io.github.diamondrubinny.scary.ui.ScaryRoot
import io.github.diamondrubinny.scary.ui.home.HomeViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: HomeViewModel by viewModels {
        HomeViewModel.factory((application as ScaryApplication).container)
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleOverride.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ScaryRoot(viewModel) }
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
