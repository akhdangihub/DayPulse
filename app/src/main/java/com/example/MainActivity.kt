package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.ai.configureAppCheck
import com.example.ui.DayPulseApp
import com.example.widget.WidgetCommands

class MainActivity : ComponentActivity() {
    private var widgetCommand by mutableStateOf<Pair<Long, String>?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure Firebase App Check with launch intent before any UI or Firebase calls.
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        val openEvening = intent?.getStringExtra("open_tab") == "evening"
        extractWidgetCommand(intent)?.let { cmd ->
            widgetCommand = System.currentTimeMillis() to cmd
        }

        setContent {
            DayPulseApp(
                startOnEveningTab = openEvening,
                widgetCommand = widgetCommand,
                onConsumeWidgetCommand = { widgetCommand = null }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractWidgetCommand(intent)?.let { cmd ->
            widgetCommand = System.currentTimeMillis() to cmd
        }
    }

    private fun extractWidgetCommand(intent: Intent?): String? {
        val explicit = intent?.getStringExtra(WidgetCommands.EXTRA_WIDGET_COMMAND)
        if (!explicit.isNullOrBlank()) return explicit
        return if (intent?.getStringExtra("open_tab") == "evening") {
            WidgetCommands.CMD_QUICK_REVIEW
        } else {
            null
        }
    }
}
