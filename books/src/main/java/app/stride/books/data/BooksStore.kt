package app.stride.books.data

import android.content.Context
import app.stride.books.model.BooksData
import app.stride.data.JsonStore

/** Books' data in books.json, on the phone only (the shared [JsonStore]). */
class BooksStore(context: Context) : JsonStore<BooksData>(context, "books.json", BooksData.serializer(), { BooksData() }, LOCK) {
    companion object {
        val LOCK = Any()
    }
}
