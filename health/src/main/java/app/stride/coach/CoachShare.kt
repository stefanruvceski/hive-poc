package app.stride.coach

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import app.stride.backup.HiveBackup
import app.stride.data.DataEvents
import app.stride.data.Store
import app.stride.domain.mondayOf
import app.stride.host.Host
import app.stride.model.AppData
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * The coach through the Claude app (no API key): [ask] copies the week's prompt, saves it as coach-<monday>.json
 * (empty "response") in the Hive folder when one is set, and opens Claude with it; the reply comes back with
 * [save] (Paste reply in the card) or by sharing it to "Hive Coach" ([ReplyActivity]).
 */
object CoachShare {
    private const val PREFS = "coach_handoff"
    private const val KEY_WEEK = "week"
    private const val CLAUDE = "com.anthropic.claude"

    /** The week the coach card reviews: last week until Sunday, then this one. */
    fun reviewWeek(today: LocalDate = LocalDate.now()): LocalDate =
        mondayOf(today).let { if (today.dayOfWeek == DayOfWeek.SUNDAY) it else it.minusWeeks(1) }

    /** The week last sent to Claude (where a shared reply goes). */
    fun pendingWeek(context: Context): LocalDate? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_WEEK, null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    fun ask(context: Context, data: AppData, monday: LocalDate) {
        val prompt = CoachPrompt.user(data, monday, LocalDate.now())
        val message = CoachHandoff.message(CoachPrompt.SYSTEM, prompt)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_WEEK, monday.toString()).apply()
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Coach prompt", message))
        Thread { writeExchange(context, monday, message, "") }.start()
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, message)
        try {
            context.startActivity(Intent(send).setPackage(CLAUDE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            context.startActivity(Intent.createChooser(send, "Ask Claude").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        Toast.makeText(context, "Prompt copied too. Send it, then share Claude's reply to Hive Coach or tap Paste reply.", Toast.LENGTH_LONG).show()
    }

    /** Stores a reply for [monday]; false when it's empty. Also fills "response" in the exchange file. */
    fun save(context: Context, monday: LocalDate, reply: String): Boolean {
        val text = CoachHandoff.clean(reply) ?: return false
        Store(context).update { it.copy(coach = it.coach + (monday.toString() to text)) }
        DataEvents.notifyChanged()
        recordReply(context, monday, text)
        return true
    }

    /** Fills "response" in the week's exchange file (in the background). */
    fun recordReply(context: Context, monday: LocalDate, text: String) {
        Thread {
            val prompt = runCatching { CoachHandoff.message(CoachPrompt.SYSTEM, CoachPrompt.user(Store(context).load(), monday, LocalDate.now())) }.getOrDefault("")
            writeExchange(context, monday, prompt, text)
        }.start()
    }

    private fun writeExchange(context: Context, monday: LocalDate, prompt: String, response: String) {
        runCatching {
            val e = CoachHandoff.Exchange(monday.toString(), prompt, response)
            HiveBackup.writeFile(context, CoachHandoff.fileName(monday), "application/json", CoachHandoff.encode(e).toByteArray())
        }
    }

    /** Share → "Hive Coach": takes Claude's reply as the review of the week last sent (or the card's week). */
    class ReplyActivity : Activity() {
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            val text = intent?.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
            val week = pendingWeek(this) ?: reviewWeek()
            if (save(this, week, text)) {
                Toast.makeText(this, "Coach review saved", Toast.LENGTH_SHORT).show()
                startActivity(Host.openIntent(this, "health/week"))
            } else {
                Toast.makeText(this, "Nothing to save", Toast.LENGTH_SHORT).show()
            }
            finish()
        }
    }
}
