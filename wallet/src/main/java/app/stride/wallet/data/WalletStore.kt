package app.stride.wallet.data

import android.content.Context
import app.stride.data.JsonStore
import app.stride.wallet.model.WalletData

/** Wallet's data in wallet.json, on the phone only (the shared [JsonStore]). */
class WalletStore(context: Context) : JsonStore<WalletData>(context, "wallet.json", WalletData.serializer(), { WalletData() }, LOCK) {
    companion object {
        val LOCK = Any()
    }
}
