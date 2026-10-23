package app.stride.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

object ExerciseLibrary {
    @Volatile private var cache: List<LibraryExercise>? = null

    suspend fun load(context: Context): List<LibraryExercise> = cache ?: withContext(Dispatchers.IO) {
        runCatching {
            val text = context.assets.open("exercises.json").bufferedReader().use { it.readText() }
            StrideJson.decodeFromString(ListSerializer(LibraryExercise.serializer()), text)
        }.getOrDefault(emptyList()).also { cache = it }
    }
}
