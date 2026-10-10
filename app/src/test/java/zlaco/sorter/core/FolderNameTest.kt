package zlaco.sorter.core

import org.junit.Assert.assertEquals
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
        assertNull(folder?.contains('/'))
    }

    @Test
    fun `replaces characters a filesystem rejects`() {
        assertEquals("a_b_c_d_e_f", FolderName.from("a:b*c?d\"e<f>g"))
    }

    @Test
    fun `strips control characters`() {
        assertEquals("AB", FolderName.from("A\u0000B"))
    }

    @Test
    fun `collapses runs of whitespace`() {
        assertEquals("Brawl Stars", FolderName.from("Brawl   Stars"))
    }

    @Test
    fun `trims trailing dots and spaces`() {
        assertEquals("Soul", FolderName.from("Soul. "))
    }

    @Test
    fun `refuses a name that is only separators`() {
        assertNull(FolderName.from("///"))
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
