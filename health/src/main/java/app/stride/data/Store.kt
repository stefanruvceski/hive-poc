package app.stride.data

import android.content.Context
import app.stride.model.AppData

/**
 * Health's data in stride.json (the shared [JsonStore]). The intervals.icu and Anthropic keys live in the
 * "secrets" prefs instead, which no backup carries (Android's cloud backup, device transfer, Hive's own).
 */
class Store(private val context: Context) : JsonStore<AppData>(context, "stride.json", AppData.serializer(), { AppData() }, LOCK) {
    private fun secrets() = context.getSharedPreferences(SECRETS, Context.MODE_PRIVATE)

    override fun afterRead(data: AppData): AppData {
        val p = secrets()
        // A file from before the split still has its keys inside; the next save moves them out.
        if (!p.contains(KEY_INTERVALS) && !p.contains(KEY_ANTHROPIC)) return data
        return data.copy(settings = data.settings.copy(apiKey = p.getString(KEY_INTERVALS, "").orEmpty(), anthropicKey = p.getString(KEY_ANTHROPIC, "").orEmpty()))
    }

    override fun beforeWrite(data: AppData): AppData {
        val s = data.settings
        secrets().edit().putString(KEY_INTERVALS, s.apiKey).putString(KEY_ANTHROPIC, s.anthropicKey).commit()
        return data.copy(settings = s.copy(apiKey = "", anthropicKey = ""))
    }

    companion object {
        /** The app and the background worker share one process; this keeps their writes apart. */
        val LOCK = Any()
        const val SECRETS = "secrets"
        private const val KEY_INTERVALS = "intervals_key"
        private const val KEY_ANTHROPIC = "anthropic_key"
    }
}
