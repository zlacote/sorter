package zlaco.sorter.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class FolderNameTest {

    @Test
    fun `keeps a plain app name`() {
        assertEquals("Soul", FolderName.from("Soul"))
    }

    @Test
    fun `keeps spaces`() {
        assertEquals("Brawl Stars", FolderName.from("Brawl Stars"))
    }

    @Test
    fun `keeps a non latin name`() {
        assertEquals("Почта", FolderName.from("Почта"))
    }

    @Test
    fun `replaces path separators so the name cannot escape the target root`() {
        val folder = FolderName.from("AC/DC")

        assertEquals("AC_DC", folder)
        assertFalse(folder!!.contains('/'))
    }

    @Test
    fun `replaces characters a filesystem rejects`() {
        assertEquals("a_b_c_d_e_f_g", FolderName.from("a:b*c?d\"e<f>g"))
    }

    @Test
    fun `replaces control characters`() {
        assertEquals("A_B", FolderName.from("A\u0000B"))
    }

    @Test
    fun `collapses runs of whitespace`() {
        assertEquals("Brawl Stars", FolderName.from("Brawl   Stars"))
    }

    @Test
    fun `trims trailing dots and spaces`() {
        assertEquals("Soul", FolderName.from("Soul. "))
    }

    /**
     * The label comes from an untrusted file name, so a name made only of
     * separators is folded to a placeholder rather than treated as unusable.
     * Refusing it would leave the file unsorted for a reason the user cannot
     * act on; a placeholder folder at least keeps the promise.
     */
    @Test
    fun `folds a name made only of separators into a placeholder`() {
        assertEquals("___", FolderName.from("///"))
    }

    @Test
    fun `refuses a name that is only whitespace`() {
        assertNull(FolderName.from("   "))
    }

    @Test
    fun `refuses parent directory names`() {
        assertNull(FolderName.from(".."))
        assertNull(FolderName.from("."))
    }

    @Test
    fun `truncates a very long name`() {
        val folder = FolderName.from("A".repeat(400))

        assertEquals(100, folder?.length)
    }

    @Test
    fun `truncation never leaves a lone surrogate behind`() {
        val folder = FolderName.from("A".repeat(99) + "\uD83D\uDE00")

        assertEquals(99, folder?.length)
        assertEquals(false, folder?.last()?.isHighSurrogate())
    }
}
