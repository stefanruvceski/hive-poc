package app.stride.music.domain

import app.stride.music.model.DlOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArgsTest {
    @Test fun speedAndSummary() {
        assertEquals("1.5", Args.fmtSpeed(1.5f))
        assertEquals("2", Args.fmtSpeed(2f))
        assertEquals("1.25", Args.fmtSpeed(1.25f))
        assertEquals("MP3 128k · 1.5×", Args.summary(DlOptions(speed = 1.5f)))
        assertEquals("MP3 128k", Args.summary(DlOptions(audioFormat = "m4a", mono = true))) // re-encode forces MP3
        assertEquals("OPUS", Args.summary(DlOptions(audioFormat = "opus")))
        assertEquals("MP4 max", Args.summary(DlOptions(audioOnly = false, videoHeight = 0)))
    }

    @Test fun atempoChains() {
        assertEquals("atempo=1.5000", Args.atempoChain(1.5f))
        assertEquals("atempo=2.0,atempo=1.5000", Args.atempoChain(3f))
        assertEquals("atempo=0.5,atempo=0.8000", Args.atempoChain(0.4f))
    }

    @Test fun splitAndExtract() {
        assertEquals(listOf("--sponsorblock-remove", "sponsor", "-o", "a b"), Args.splitArgs("--sponsorblock-remove sponsor -o \"a b\""))
        assertEquals("https://youtu.be/abc", Args.extractUrl("Watch this: https://youtu.be/abc."))
        assertNull(Args.extractUrl("no link"))
    }

    @Test fun audioArgs() {
        val a = Args.buildArgs(DlOptions(speed = 1.5f, normalize = true, mono = true), "/tmp/x")
        assertTrue(("-x" to null) in a)
        assertTrue(("--audio-format" to "mp3") in a)
        assertTrue(("--postprocessor-args" to "ExtractAudio:-filter:a loudnorm=I=-16:TP=-1.5:LRA=11,atempo=1.5000 -ac 1") in a)
        assertTrue(("--no-playlist" to null) in a)
    }

    @Test fun videoAndExtras() {
        val a = Args.buildArgs(DlOptions(audioOnly = false, videoHeight = 720, playlist = true, extraArgs = "--limit-rate 2M"), "/d")
        assertTrue(("-S" to "res:720,vcodec:h264,acodec:aac") in a)
        assertTrue(("--yes-playlist" to null) in a)
        assertTrue(("--limit-rate" to null) in a && ("2M" to null) in a)
        assertFalse(a.any { it.first == "-x" })
    }

    @Test fun texts() {
        assertEquals("Song · Chorus", Args.displayName("Song ‹03‹ Chorus.mp3"))
        assertEquals("42%  ·  1:05 left", Args.progressText(42f, 65))
        assertEquals("Processing…", Args.progressText(100f, 0))
    }
}
