package app.stride

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.IntentCompat
import androidx.compose.runtime.mutableStateOf
import app.stride.host.Host
import app.stride.host.ModuleHost
import app.stride.host.OpenRequest
import app.stride.ui.theme.StrideTheme
import app.stride.whatsnew.WhatsNewGate

/** The one activity: hosts the modules and turns intents (widgets, notifications, shortcuts) into open requests. */
class MainActivity : ComponentActivity() {
    // Where to open ("workout", a tab id, "day:YYYY-MM-DD", "plan-review"), with a counter so repeats still fire.
    private val openRequest = mutableStateOf(OpenRequest(0, ""))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        if (savedInstanceState == null) handle(intent)
        setContent {
            StrideTheme {
                ModuleHost(Modules.all, openRequest.value)
                WhatsNewGate()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        // A picture shared from another app opens in Drawify.
        if (intent?.action == Intent.ACTION_SEND && intent.type?.startsWith("image/") == true) {
            IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let { uri ->
                openRequest.value = OpenRequest(openRequest.value.id + 1, "draw/share:$uri")
            }
            return
        }
        intent?.getStringExtra(Host.EXTRA_OPEN)?.let { target ->
            intent.removeExtra(Host.EXTRA_OPEN)
            openRequest.value = OpenRequest(openRequest.value.id + 1, target)
        }
    }
}
