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
import app.stride.coach.CoachHandoff.Kind
import app.stride.data.DataEvents
import app.stride.data.Store
import app.stride.domain.mondayOf
import app.stride.host.Host
import app.stride.model.AppData
import app.stride.notify.Notifier
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * The coach through the Claude app (no API key), weekly and daily: [ask] copies the prompt, saves it as
 * coach-<monday>.json / coach-day-<date>.json (empty "response") in the Hive folder when one is set, and opens
 * Claude with it; the reply comes back with [save] (Paste reply) or by sharing it to "Hive Coach" ([ReplyActivity]).
 */
object CoachShare {
    private const val PREFS = "coach_handoff"
    private const val KEY_PENDING = "week"
    private const val CLAUDE = "com.anthropic.claude"
    private const val EXTRA_KIND = "coach_kind"

    /** The week the coach card reviews: last week until Sunday, then this one. */
    fun reviewWeek(today: LocalDate = LocalDate.now()): LocalDate =
        mondayOf(today).let { if (today.dayOfWeek == DayOfWeek.SUNDAY) it else it.minusWeeks(1) }

    /** What was last sent to Claude (where a shared reply goes). */
    fun pending(context: Context): Pair<Kind, LocalDate>? =
        CoachHandoff.parsePending(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_PENDING, null))

    private fun message(data: AppData, kind: Kind, date: LocalDate): String = when (kind) {
        Kind.WEEK -> CoachHandoff.message(CoachPrompt.SYSTEM, CoachPrompt.user(data, date, LocalDate.now()))
        Kind.DAY -> CoachHandoff.dailyMessage(CoachPrompt.DAILY_SYSTEM, CoachPrompt.daily(data, date))
    }

    fun ask(context: Context, data: AppData, monday: LocalDate) = ask(context, data, Kind.WEEK, monday)

    /** [date]: the week's Monday, or the day of a daily check-in. */
    fun ask(context: Context, data: AppData, kind: Kind, date: LocalDate) {
        val text = message(data, kind, date)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_PENDING, CoachHandoff.pendingKey(kind, date)).apply()
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Coach prompt", text))
        Thread { writeExchange(context, kind, date, text, "") }.start()
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        try {
            context.startActivity(Intent(send).setPackage(CLAUDE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            context.startActivity(Intent.createChooser(send, "Ask Claude").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        Toast.makeText(context, "Prompt copied too. Send it, then share Claude's reply to Hive Coach or tap Paste reply.", Toast.LENGTH_LONG).show()
    }

    /** Stores a reply; false when it's empty. Also fills "response" in the exchange file. */
    fun save(context: Context, kind: Kind, date: LocalDate, reply: String): Boolean {
        val text = CoachHandoff.clean(reply) ?: return false
        Store(context).update { put(it, kind, date, text) }
        DataEvents.notifyChanged()
        recordReply(context, kind, date, text)
        return true
    }

    fun put(d: AppData, kind: Kind, date: LocalDate, text: String): AppData = when (kind) {
        Kind.WEEK -> d.copy(coach = d.coach + (date.toString() to text))
        Kind.DAY -> d.copy(coachDays = (d.coachDays + (date.toString() to text)).entries.sortedBy { it.key }.takeLast(120).associate { it.toPair() })
    }

    fun recordReply(context: Context, monday: LocalDate, text: String) = recordReply(context, Kind.WEEK, monday, text)

    /** Fills "response" in the exchange file (in the background). */
    fun recordReply(context: Context, kind: Kind, date: LocalDate, text: String) {
        Thread {
            val prompt = runCatching { message(Store(context).load(), kind, date) }.getOrDefault("")
            writeExchange(context, kind, date, prompt, text)
        }.start()
    }

    private fun writeExchange(context: Context, kind: Kind, date: LocalDate, prompt: String, response: String) {
        runCatching {
            val e = CoachHandoff.Exchange(date.toString(), prompt, response)
            HiveBackup.writeFile(context, CoachHandoff.fileName(kind, date), "application/json", CoachHandoff.encode(e).toByteArray())
        }
    }

    /** A coach notification: the tap opens the coach screen, the button goes straight to the Claude app. */
    fun notify(context: Context, kind: Kind, id: Int, title: String, text: String): Boolean = Notifier.post(
        context, if (kind == Kind.DAY) "daily" else "progress", if (kind == Kind.DAY) "Daily plan" else "Workouts and progress",
        id, title, text, "coach",
        action = "Ask Claude" to Intent(context, AskActivity::class.java).putExtra(EXTRA_KIND, kind.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )

    /** The notification's "Ask Claude" button: builds today's (or the week's) prompt and opens Claude, no screen. */
    class AskActivity : Activity() {
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            val kind = Kind.entries.firstOrNull { it.id == intent?.getStringExtra(EXTRA_KIND) } ?: Kind.DAY
            val date = if (kind == Kind.DAY) LocalDate.now() else reviewWeek()
            runCatching { ask(this, Store(this).load(), kind, date) }
                .onFailure { Toast.makeText(this, "Couldn't open Claude: ${it.message}", Toast.LENGTH_LONG).show() }
            finish()
        }
    }

    /** Share → "Hive Coach": takes Claude's reply as the check-in or review last sent (or last week's review). */
    class ReplyActivity : Activity() {
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            val text = intent?.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
            val (kind, date) = pending(this) ?: (Kind.WEEK to reviewWeek())
            if (save(this, kind, date, text)) {
                Toast.makeText(this, if (kind == Kind.DAY) "Daily check-in saved" else "Coach review saved", Toast.LENGTH_SHORT).show()
                startActivity(Host.openIntent(this, "health/coach"))
            } else {
                Toast.makeText(this, "Nothing to save", Toast.LENGTH_SHORT).show()
            }
            finish()
        }
    }
}
