package app.stride.data

import android.content.Context
import app.stride.model.AppData

/** Health's data in stride.json (the shared [JsonStore]). */
class Store(context: Context) : JsonStore<AppData>(context, "stride.json", AppData.serializer(), { AppData() }, LOCK) {
    companion object {
        /** The app and the background worker share one process; this keeps their writes apart. */
        val LOCK = Any()
    }
}
