package online.aireport.split.core

import java.io.File
import kotlinx.serialization.Serializable
import online.aireport.split.data.api.SplitJson
import online.aireport.split.data.model.AmountDto
import online.aireport.split.data.model.SplitDto
import online.aireport.split.data.model.toDomain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Runs the shared vectors of `spec/split-vectors.json` against the Kotlin port of computeShares. */
@RunWith(Parameterized::class)
class SplitVectorsTest(private val case: VectorCase) {

    @Test
    fun matchesTheSharedVector() {
        val split = case.split.toDomain()
        if (case.error != null) {
            val thrown = assertThrows(SplitException::class.java) { computeShares(case.amountCents, split) }
            assertEquals(case.error, thrown.code.code)
        } else {
            val expected = checkNotNull(case.expected) { "${case.name}: neither expected nor error" }
            assertEquals(expected.map { Share(it.memberId, it.amountCents) }, computeShares(case.amountCents, split))
        }
    }

    @Serializable
    data class VectorCase(
        val name: String,
        val amountCents: Long,
        val split: SplitDto,
        val expected: List<AmountDto>? = null,
        val error: String? = null,
    ) {
        override fun toString() = name
    }

    @Serializable
    private class VectorFile(val cases: List<VectorCase>)

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun cases(): List<VectorCase> {
            val path = checkNotNull(System.getProperty("split.vectors")) {
                "The split.vectors system property must point at spec/split-vectors.json"
            }
            val cases = SplitJson.decodeFromString<VectorFile>(File(path).readText()).cases
            assertTrue("No vectors found in $path", cases.isNotEmpty())
            return cases
        }
    }
}
