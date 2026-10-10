package zlaco.sorter.core

import org.junit.Assert.assertEquals
import org.junit.Test

class SortPolicyTest {

    private val config = SortConfig(
        sourceFolders = setOf("DCIM/Screenshots/", "DCIM/Screen recordings/"),
        targetRoot = "Pictures/AppScreenshots/",
        includeScreenRecordings = true,
    )

    private fun candidate(
        id: Long = 1L,
        name: String = "Screenshot_20261010_064921_Soul.png",
        path: String = "DCIM/Screenshots/",
    ) = MediaCandidate(
        id = id,
        displayName = name,
        relativePath = path,
        mimeType = "image/png",
        capturedAtMillis = 0L,
    )

    private fun decide(
        candidate: MediaCandidate,
        config: SortConfig = this.config,
        handled: Set<Long> = emptySet(),
    ) = SortPolicy.decide(candidate, config) { it in handled }

    @Test
    fun `moves a screenshot into a folder named after its app`() {
        val verdict = decide(candidate())

        assertEquals(
            SortVerdict.Move("Soul", "Soul", "Pictures/AppScreenshots/Soul/"),
            verdict,
        )
    }

    @Test
    fun `keeps spaces in the folder name`() {
        val verdict = decide(candidate(name = "Screenshot_20261001_184114_Brawl Stars.png"))

        assertEquals(
            SortVerdict.Move("Brawl Stars", "Brawl Stars", "Pictures/AppScreenshots/Brawl Stars/"),
            verdict,
        )
    }

    @Test
    fun `moves a screen recording`() {
        val verdict = decide(
            candidate(
                name = "Screen_Recording_20260928_220747_Brawl Stars.mp4",
                path = "DCIM/Screen recordings/",
            )
        )

        assertEquals(
            SortVerdict.Move("Brawl Stars", "Brawl Stars", "Pictures/AppScreenshots/Brawl Stars/"),
            verdict,
        )
    }

    @Test
    fun `leaves recordings alone when they are switched off`() {
        val verdict = decide(
            candidate(
                name = "Screen_Recording_20260928_220747_Brawl Stars.mp4",
                path = "DCIM/Screen recordings/",
            ),
            config = config.copy(includeScreenRecordings = false),
        )

        assertEquals(SortVerdict.Skip(SkipReason.RECORDINGS_EXCLUDED), verdict)
    }

    @Test
    fun `leaves a lock screen capture alone`() {
        val verdict = decide(candidate(name = "Screenshot_20261010_064921.png"))

        assertEquals(SortVerdict.Skip(SkipReason.NO_APP_LABEL), verdict)
    }

    @Test
    fun `leaves an image an app saved on its own alone`() {
        // Same timestamp shape as a capture, but no capture prefix. Moving this
        // would file somebody else's download under a folder named after a
        // number.
        val verdict = decide(candidate(name = "IMG_20260928_175755_248.jpg"))

        assertEquals(SortVerdict.Skip(SkipReason.NOT_A_CAPTURE), verdict)
    }

    @Test
    fun `leaves a file from another folder alone`() {
        val verdict = decide(candidate(path = "DCIM/Camera/"))

        assertEquals(SortVerdict.Skip(SkipReason.NOT_IN_SOURCE), verdict)
    }

    @Test
    fun `leaves a name without a timestamp alone`() {
        val verdict = decide(candidate(name = "Screenshot.png"))

        assertEquals(SortVerdict.Skip(SkipReason.UNREADABLE_NAME), verdict)
    }

    @Test
    fun `leaves a file the user already filed by hand`() {
        val verdict = decide(candidate(path = "DCIM/Screenshots/My Own Folder/"))

        assertEquals(SortVerdict.Skip(SkipReason.NOT_IN_SOURCE), verdict)
    }

    /**
     * Moving a file announces a change, which brings it straight back through
     * the trigger. This is the test that keeps that from becoming a loop.
     */
    @Test
    fun `never moves a file that is already under the target root`() {
        val verdict = decide(candidate(path = "Pictures/AppScreenshots/Soul/"))

        assertEquals(SortVerdict.Skip(SkipReason.ALREADY_SORTED), verdict)
    }

    @Test
    fun `never moves a file the journal already records`() {
        val verdict = decide(candidate(id = 7L), handled = setOf(7L))

        assertEquals(SortVerdict.Skip(SkipReason.ALREADY_HANDLED), verdict)
    }

    @Test
    fun `the journal alone stops the loop even without the path check`() {
        // The path barrier would not fire here: the file is back in the source
        // folder, yet its id is known. The journal still refuses it.
        val verdict = decide(candidate(id = 9L), handled = setOf(9L))

        assertEquals(SortVerdict.Skip(SkipReason.ALREADY_HANDLED), verdict)
    }

    @Test
    fun `accepts source folders written without a trailing slash`() {
        val verdict = decide(candidate(), config = config.copy(sourceFolders = setOf("DCIM/Screenshots")))

        assertEquals(true, verdict is SortVerdict.Move)
    }

    /**
     * The name is untrusted, so a label carrying a character a filesystem
     * rejects is neutralised into a placeholder rather than reaching the disk
     * and failing the move on the device.
     */
    @Test
    fun `neutralises a label carrying a rejected character`() {
        val verdict = decide(candidate(name = "Screenshot_20261010_064921_x:y.png"))

        assertEquals(
            SortVerdict.Move("x:y", "x_y", "Pictures/AppScreenshots/x_y/"),
            verdict,
        )
    }
}
