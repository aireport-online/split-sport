package online.aireport.split.core

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TextHelpersTest {
    @Test
    fun `initials are the first letters of the first two words`() {
        assertEquals("WT", initials("Weekendje Texel"))
        assertEquals("A", initials("Anna"))
        assertEquals("HV", initials("  huis  van  bas "))
        assertEquals("", initials("   "))
        assertEquals("😀B", initials("😀 bas"))
    }

    @Test
    fun `invite codes are sanitised while typing`() {
        assertEquals("ABCD2345", InviteCode.sanitize("abcd-2345"))
        assertEquals("ABCD2345", InviteCode.sanitize("abcd2345xyz"))
        assertEquals("AB", InviteCode.sanitize("a1b0"))
        assertTrue(InviteCode.isValid("ABCD2345"))
        assertFalse(InviteCode.isValid("ABCD234"))
        assertFalse(InviteCode.isValid("ABCD2340"))
    }

    @Test
    fun `invite codes are found in share links`() {
        assertEquals("ABCD2345", InviteCode.fromLink("split://join/ABCD2345"))
        assertEquals("ABCD2345", InviteCode.fromLink("https://split.example.org/join/abcd2345"))
        assertEquals("ABCD2345", InviteCode.fromLink("http://10.0.2.2:8787/join/ABCD2345/?ref=share"))
        assertEquals("ABCD2345", InviteCode.fromLink(" abcd2345 "))
        assertNull(InviteCode.fromLink("https://split.example.org/groups/ABCD2345"))
        assertNull(InviteCode.fromLink("split://join/short"))
    }

    @Test
    fun `the share link points at the server's join page`() {
        assertEquals("https://split.example.org/join/ABCD2345", InviteCode.shareLink("https://split.example.org/", "ABCD2345"))
    }

    @Test
    fun `short dates have no dot and only show another year`() {
        val today = LocalDate.of(2026, 11, 9)
        assertEquals("2 nov", formatShortDate(LocalDate.of(2026, 11, 2), today))
        assertEquals("31 dec 2025", formatShortDate(LocalDate.of(2025, 12, 31), today))
        assertEquals("2 november 2026", formatLongDate(LocalDate.of(2026, 11, 2)))
    }

    @Test
    fun `API dates parse or are null`() {
        assertEquals(LocalDate.of(2026, 11, 2), parseIsoDate("2026-11-02"))
        assertNull(parseIsoDate("2 nov"))
    }
}
