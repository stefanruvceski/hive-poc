package app.stride.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BackupFormatTest {
    @Test fun names() {
        assertEquals("hive-backup-2026-10-26.zip", BackupFormat.fileName(LocalDate.parse("2026-10-26")))
        assertEquals(LocalDate.parse("2026-10-26"), BackupFormat.dateOf("hive-backup-2026-10-26.zip"))
        assertNull(BackupFormat.dateOf("hive-backup-2026-10-26 (1).zip"))
        assertNull(BackupFormat.dateOf("stride-backup-2026-10-26.json"))
        assertEquals("hive-backup-2026-10-26.zip", BackupFormat.newest(listOf("hive-backup-2026-09-01.zip", "notes.txt", "hive-backup-2026-10-26.zip")))
    }

    @Test fun keepsTwoWeeksAndOnePerMonth() {
        val start = LocalDate.parse("2026-01-01")
        val names = (0 until 300).map { BackupFormat.fileName(start.plusDays(it.toLong())) } + "other.zip"
        val gone = BackupFormat.toDelete(names).toSet()
        val kept = names.filter { it !in gone && it != "other.zip" }.map { BackupFormat.dateOf(it)!! }
        assertFalse("other.zip" in gone)
        // the 14 newest days, plus the newest of each earlier month (Jan–Sep) = 14 + 9
        assertEquals(23, kept.size)
        assertTrue(LocalDate.parse("2026-10-27") in kept)
        assertTrue(LocalDate.parse("2026-01-31") in kept)
        assertFalse(LocalDate.parse("2026-01-30") in kept)
    }

    @Test fun zipRoundTripAndModuleFiles() {
        val files = mapOf("manifest.json" to "{}".toByteArray(), "habits/habits.json" to "[1]".toByteArray(), "wallet/wallet_cards/a.jpg" to byteArrayOf(1, 2, 3))
        val back = BackupFormat.unpack(BackupFormat.pack(files))
        assertEquals(files.keys, back.keys)
        assertArrayEquals(byteArrayOf(1, 2, 3), back["wallet/wallet_cards/a.jpg"])
        assertEquals(setOf("wallet_cards/a.jpg"), BackupFormat.moduleFiles(back, "wallet").keys)
    }

    @Test fun unsafePaths() {
        assertFalse(BackupFormat.safe("../evil"))
        assertFalse(BackupFormat.safe("/abs"))
        assertFalse(BackupFormat.safe("a//b"))
        assertTrue(BackupFormat.safe("health/stride.json"))
    }
}
