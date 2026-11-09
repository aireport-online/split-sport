package online.aireport.split.data

import online.aireport.split.data.api.ServerUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerUrlTest {
    @Test
    fun `trims spaces and trailing slashes`() {
        assertEquals("https://split.example.org", ServerUrl.normalize("  https://split.example.org/ "))
        assertEquals("http://10.0.2.2:8787", ServerUrl.normalize("http://10.0.2.2:8787"))
    }

    @Test
    fun `assumes https when no scheme was typed`() {
        assertEquals("https://split.example.org", ServerUrl.normalize("split.example.org"))
        assertEquals("https://split.example.org:8443", ServerUrl.normalize("split.example.org:8443/"))
    }

    @Test
    fun `rejects what is not an http address`() {
        assertNull(ServerUrl.normalize(""))
        assertNull(ServerUrl.normalize("   "))
        assertNull(ServerUrl.normalize("http://"))
        assertNull(ServerUrl.normalize("ftp://split.example.org"))
        assertNull(ServerUrl.normalize("https://split example.org"))
    }

    @Test
    fun `the API lives under api`() {
        assertEquals("https://split.example.org/api/", ServerUrl.apiBaseUrl("https://split.example.org").toString())
        assertEquals("https://example.org/split/api/", ServerUrl.apiBaseUrl("https://example.org/split").toString())
        assertNull(ServerUrl.apiBaseUrl(""))
    }
}
