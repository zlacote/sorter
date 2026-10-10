package zlaco.sorter.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cases use names that exist on a real One UI device, not invented ones. A
 * parser that only passes on tidy examples is worthless against a shell whose
 * exact formatting is the whole input.
 */
class CaptureNameTest {

    @Test
    fun `reads app label from a screenshot name`() {
        val parsed = CaptureNames.parse("Screenshot_20261010_064921_Soul.png")

        assertEquals(CaptureKind.SCREENSHOT, parsed?.kind)
        assertEquals("Soul", parsed?.appLabel)
    }

    @Test
    fun `keeps spaces inside the app label`() {
        val parsed = CaptureNames.parse("Screenshot_20261001_184114_Brawl Stars.png")

        assertEquals("Brawl Stars", parsed?.appLabel)
    }

    @Test
    fun `keeps a multi word app label`() {
        val parsed = CaptureNames.parse("Screenshot_20260926_124435_Google Play Store.png")

        assertEquals("Google Play Store", parsed?.appLabel)
    }

    @Test
    fun `recognises screen recordings`() {
        val parsed = CaptureNames.parse("Screen_Recording_20260928_220747_Brawl Stars.mp4")

        assertEquals(CaptureKind.SCREEN_RECORDING, parsed?.kind)
        assertEquals("Brawl Stars", parsed?.appLabel)
    }

    @Test
    fun `recognises the launcher as an app`() {
        val parsed = CaptureNames.parse("Screen_Recording_20260921_141105_One UI Home.mp4")

        assertEquals("One UI Home", parsed?.appLabel)
    }

    @Test
    fun `returns no label for a lock screen capture`() {
        val parsed = CaptureNames.parse("Screenshot_20261010_064921.png")

        assertEquals(CaptureKind.SCREENSHOT, parsed?.kind)
        assertNull(parsed?.appLabel)
    }

    @Test
    fun `returns no label for a stock name with a dashed stamp`() {
        val parsed = CaptureNames.parse("Screenshot_20201215-090626.png")

        assertEquals(CaptureKind.SCREENSHOT, parsed?.kind)
        assertNull(parsed?.appLabel)
    }

    @Test
    fun `treats a trailing separator as no label`() {
        assertNull(CaptureNames.parse("Screenshot_20261010_064921_.png")?.appLabel)
    }

    @Test
    fun `resolves the capture instant`() {
        val parsed = CaptureNames.parse("Screenshot_20261010_064921_Soul.png")

        val expected = java.time.LocalDateTime.of(2026, 10, 10, 6, 49, 21)
            .atZone(java.time.ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        assertEquals(expected, parsed?.capturedAtMillis)
    }

    @Test
    fun `rejects an impossible month`() {
        assertNull(CaptureNames.parse("Screenshot_20261310_064921_Soul.png"))
    }

    @Test
    fun `rejects an impossible hour`() {
        assertNull(CaptureNames.parse("Screenshot_20261010_256921_Soul.png"))
    }

    @Test
    fun `rejects a day the month does not have`() {
        assertNull(CaptureNames.parse("Screenshot_20260230_064921_Soul.png"))
    }

    @Test
    fun `rejects a name with no stamp at all`() {
        assertNull(CaptureNames.parse("Screenshot.png"))
        assertNull(CaptureNames.parse("screenshot.jpg"))
    }

    /**
     * An image an app saved on its own does carry the same timestamp shape, so
     * the prefix is the only thing that distinguishes it from a real capture.
     * The policy relies on this to refuse it.
     */
    @Test
    fun `reads an app saved image as an unknown kind with no label`() {
        val parsed = CaptureNames.parse("IMG_20260928_175755_248.jpg")

        assertEquals(CaptureKind.UNKNOWN, parsed?.kind)
        assertNull(parsed?.appLabel)
    }

    @Test
    fun `rejects an all digit label as a malformed remainder`() {
        assertNull(CaptureNames.parse("Screenshot_20261010_064921_12345.png")?.appLabel)
    }

    @Test
    fun `treats a dot inside the app label as part of the name`() {
        val parsed = CaptureNames.parse("Screenshot_20261010_064921_Season 2.Finale.png")

        assertEquals("Season 2.Finale", parsed?.appLabel)
    }

    @Test
    fun `keeps a non latin app label intact`() {
        val parsed = CaptureNames.parse("Screenshot_20261010_064921_Почта Mail.png")

        assertEquals("Почта Mail", parsed?.appLabel)
    }

    @Test
    fun `reads every screenshot name present on the test device`() {
        val names = listOf(
            "Screenshot_20260921_143335_Cherrygram.jpg",
            "Screenshot_20260921_152959_TikTok.jpg",
            "Screenshot_20260924_133800_Brawl Stars.png",
            "Screenshot_20260926_124435_Google Play Store.png",
            "Screenshot_20260928_205619_Soul.png",
            "Screenshot_20261001_184114_Brawl Stars.png",
            "Screenshot_20261003_195740_Chrome.png",
            "Screenshot_20261004_113617_ChatGPT.png",
            "Screenshot_20261006_141638_Chromium.png",
            "Screenshot_20261007_153856_AyuGram.png",
            "Screenshot_20261008_180951_ArchiveTune.png",
            "Screenshot_20261010_064921_Soul.png",
        )

        val labels = names.map { CaptureNames.parse(it)?.appLabel }

        assertTrue("every name must yield an app label", labels.none { it == null })
        assertEquals(
            listOf(
                "Cherrygram", "TikTok", "Brawl Stars", "Google Play Store",
                "Soul", "Brawl Stars", "Chrome", "ChatGPT", "Chromium",
                "AyuGram", "ArchiveTune", "Soul",
            ),
            labels,
        )
    }
}
