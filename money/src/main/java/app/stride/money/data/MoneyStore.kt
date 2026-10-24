package app.stride.money.data

import android.content.Context
import app.stride.data.JsonStore
import app.stride.money.model.MoneyData

/** Money's data in money.json, on the phone only (the shared [JsonStore]). */
class MoneyStore(context: Context) : JsonStore<MoneyData>(context, "money.json", MoneyData.serializer(), { MoneyData() }, LOCK) {
    companion object {
        val LOCK = Any()
    }
}
