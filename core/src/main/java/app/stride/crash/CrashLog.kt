package app.stride.crash

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.stride.host.AppInfo
import app.stride.ui.FCard
import app.stride.ui.theme.StrideColors
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.LocalDateTime

/**
 * Keeps the last crash's stack trace (filesDir/last_crash.txt) so the next launch can show it and the user can
 * share it; the crash itself still goes to the system handler (the "keeps stopping" dialog).
 */
object CrashLog {
    private const val FILE = "last_crash.txt"

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            runCatching {
                val trace = StringWriter().also { e.printStackTrace(PrintWriter(it)) }.toString()
                File(app.filesDir, FILE).writeText(
                    "Hive ${AppInfo.versionName} (build ${AppInfo.versionCode}), Android ${Build.VERSION.RELEASE} (${Build.MODEL})\n" +
                        "${LocalDateTime.now()} on thread ${thread.name}\n\n$trace",
                )
            }
            previous?.uncaughtException(thread, e)
        }
    }

    fun read(context: Context): String? = File(context.filesDir, FILE).takeIf { it.exists() }?.readText()
    fun clear(context: Context) { File(context.filesDir, FILE).delete() }
}

/** On home after a crash: what broke (first line) with Share and Dismiss. */
@Composable
fun CrashCard() {
    val context = LocalContext.current
    var text by remember { mutableStateOf(CrashLog.read(context)) }
    val t = text ?: return
    val cause = t.lines().drop(3).firstOrNull { it.isNotBlank() }.orEmpty()
    FCard {
        Text("Hive crashed last time", style = MaterialTheme.typography.titleMedium)
        Text(cause, style = MaterialTheme.typography.bodySmall, color = StrideColors.Danger, maxLines = 3, modifier = Modifier.padding(vertical = 6.dp))
        Text("Share the details so it can be fixed.", style = MaterialTheme.typography.bodySmall, color = StrideColors.Dim)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            OutlinedButton(onClick = {
                context.startActivity(
                    Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, t), "Share crash details")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }) { Text("Share details") }
            TextButton(onClick = { CrashLog.clear(context); text = null }) { Text("Dismiss") }
        }
    }
}
