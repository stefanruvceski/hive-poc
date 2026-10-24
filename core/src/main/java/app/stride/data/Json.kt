package app.stride.data

import kotlinx.serialization.json.Json

/** JSON settings shared by every module: tolerant of unknown or missing fields so stored data survives updates. */
val StrideJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    coerceInputValues = true
    isLenient = true
}
