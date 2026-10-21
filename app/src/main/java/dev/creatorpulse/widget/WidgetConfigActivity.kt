package dev.creatorpulse.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dev.creatorpulse.App
import dev.creatorpulse.MainActivity
import dev.creatorpulse.data.Fmt
import dev.creatorpulse.ui.components.GameIcon
import dev.creatorpulse.ui.theme.CreatorPulseTheme
import kotlinx.coroutines.launch

/** Lets the user pick which experience an Experience widget shows. */
class WidgetConfigActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val appWidgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        val settings = App.get(this).settings

        setContent {
            CreatorPulseTheme {
                val snapshot by settings.snapshot.collectAsStateWithLifecycle(initialValue = null)
                Scaffold(topBar = { TopAppBar(title = { Text("Choose an experience") }) }) { padding ->
                    val list = snapshot?.experiences.orEmpty()
                    if (list.isEmpty()) {
                        Column(Modifier.fillMaxSize().padding(padding).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("No experiences loaded yet. Open Creator Pulse, sign in or add an experience, then add the widget again.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Button(onClick = { startActivity(Intent(this@WidgetConfigActivity, MainActivity::class.java)); finish() }) { Text("Open Creator Pulse") }
                        }
                    } else {
                        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(list, key = { it.universeId }) { e ->
                                Card(Modifier.fillMaxWidth().clickable { choose(appWidgetId, e.universeId) }) {
                                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        GameIcon(e.iconUrl, size = 44)
                                        Spacer(Modifier.width(12.dp))
                                        Column {
                                            Text(e.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text("${Fmt.compact(e.playing)} playing · ${Fmt.compact(e.visits)} visits", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun choose(appWidgetId: Int, universeId: Long) {
        lifecycleScope.launch {
            runCatching { bindExperienceWidget(this@WidgetConfigActivity, appWidgetId, universeId) }
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
            finish()
        }
    }
}
