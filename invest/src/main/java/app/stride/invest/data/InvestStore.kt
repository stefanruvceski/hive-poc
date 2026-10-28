package app.stride.invest.data

import android.content.Context
import app.stride.data.JsonStore
import app.stride.invest.model.InvestData

/** Invest's data in invest.json, on the phone only (the shared [JsonStore]). */
class InvestStore(context: Context) : JsonStore<InvestData>(context, "invest.json", InvestData.serializer(), { InvestData() }, LOCK) {
    companion object {
        val LOCK = Any()
    }
}
