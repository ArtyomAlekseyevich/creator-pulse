package dev.creatorpulse

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.creatorpulse.ui.AppRoot
import dev.creatorpulse.ui.MainViewModel
import dev.creatorpulse.ui.theme.CreatorPulseTheme

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    private var pendingUniverse by mutableStateOf<Long?>(null)
    private var pendingTab by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        consume(intent)
        setContent {
            CreatorPulseTheme {
                AppRoot(
                    vm = vm,
                    requestedUniverse = pendingUniverse,
                    requestedTab = pendingTab,
                    onRequestConsumed = { pendingUniverse = null; pendingTab = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        consume(intent)
    }

    private fun consume(intent: Intent?) {
        intent ?: return
        intent.getLongExtra(EXTRA_UNIVERSE, -1L).takeIf { it > 0 }?.let { pendingUniverse = it }
        intent.getStringExtra(EXTRA_TAB)?.let { pendingTab = it }
    }

    companion object {
        const val EXTRA_UNIVERSE = "universeId"
        const val EXTRA_TAB = "tab"
    }
}
