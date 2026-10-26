package app.stride.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** The Hive backup file: names, which old copies to keep, and the zip itself (pure, unit tested). */
object BackupFormat {
    private const val PREFIX = "hive-backup-"
    private val NAME = Regex("""^hive-backup-(\d{4}-\d{2}-\d{2})\.zip$""")

    fun fileName(date: LocalDate) = "$PREFIX$date.zip"

    fun dateOf(name: String): LocalDate? = NAME.find(name)?.groupValues?.get(1)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    /** Of the backups in the folder, the ones to delete: keep the [daily] newest, plus the newest of each of the last [months] months. */
    fun toDelete(names: List<String>, daily: Int = 14, months: Int = 12): List<String> {
        val dated = names.mapNotNull { n -> dateOf(n)?.let { n to it } }.sortedByDescending { it.second }
        val keep = dated.take(daily).map { it.first }.toMutableSet()
        dated.groupBy { it.second.year * 12 + it.second.monthValue }.entries
            .sortedByDescending { it.key }.take(months)
            .forEach { (_, list) -> keep += list.first().first }
        return dated.map { it.first }.filter { it !in keep }
    }

    /** The newest Hive backup among [names], or null. */
    fun newest(names: List<String>): String? = names.filter { dateOf(it) != null }.maxByOrNull { dateOf(it)!! }

    fun pack(files: Map<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            files.toSortedMap().forEach { (name, bytes) ->
                z.putNextEntry(ZipEntry(name))
                z.write(bytes)
                z.closeEntry()
            }
        }
        return out.toByteArray()
    }

    /** Entries of a zip; paths that try to leave their folder ("..", absolute) are skipped. */
    fun unpack(zip: ByteArray): Map<String, ByteArray> {
        val out = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(zip)).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                if (!e.isDirectory && safe(e.name)) out[e.name] = z.readBytes()
                z.closeEntry()
            }
        }
        return out
    }

    fun safe(path: String): Boolean = path.isNotBlank() && !path.startsWith("/") && !path.contains('\\') && path.split('/').none { it == ".." || it.isEmpty() }

    /** Files of the module [id] in a backup, without the "id/" prefix. */
    fun moduleFiles(all: Map<String, ByteArray>, id: String): Map<String, ByteArray> =
        all.filterKeys { it.startsWith("$id/") }.mapKeys { it.key.removePrefix("$id/") }
}
