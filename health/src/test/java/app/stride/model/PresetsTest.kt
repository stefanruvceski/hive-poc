package app.stride.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PresetsTest {
    private val library = Json.parseToJsonElement(File("src/main/assets/exercises.json").readText()).jsonArray
        .map { it.jsonObject }.associateBy { it["id"]!!.jsonPrimitive.content }

    @Test fun everyPresetIsAKettlebellExerciseInTheLibrary() {
        val presets = DayKind.entries.flatMap { DefaultPlan.presets(it) }
        assertTrue(presets.isNotEmpty())
        presets.forEach { p ->
            val e = library[p.dbId] ?: error("${p.name}: ${p.dbId} is not in exercises.json")
            val kb = e["equipment"]?.jsonPrimitive?.content == "kettlebells" || e["name"]!!.jsonPrimitive.content.contains("kettlebell", true) ||
                p.dbId == "Goblet_Squat"
            assertTrue("${p.name} → ${p.dbId} is not a kettlebell exercise", kb)
            e["img"]?.jsonPrimitive?.content?.let { img -> assertTrue("${p.dbId}: photo $img missing", img in library) }
        }
    }

    @Test fun oldSessionsFollowThePreset() {
        assertEquals("KB_Bicep_Curl", Preset.dbIdFor(" kettlebell curl"))
        assertEquals(null, Preset.dbIdFor("Bench press"))
    }
}
