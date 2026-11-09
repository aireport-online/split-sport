package online.aireport.split.data

import online.aireport.split.data.receipt.ReceiptImageSize
import org.junit.Assert.assertEquals
import org.junit.Test

class ReceiptImageSizeTest {
    @Test
    fun `a small photo is left as it is`() {
        assertEquals(1, ReceiptImageSize.sampleSize(1200, 1600))
        assertEquals(1f, ReceiptImageSize.scale(1200, 1600))
    }

    @Test
    fun `a large photo is sampled down but never below 1600 px`() {
        assertEquals(2, ReceiptImageSize.sampleSize(3000, 4000))
        assertEquals(4, ReceiptImageSize.sampleSize(8160, 6120))
        assertEquals(1, ReceiptImageSize.sampleSize(3100, 2000))
    }

    @Test
    fun `the long side ends up at 1600 px`() {
        assertEquals(0.8f, ReceiptImageSize.scale(1500, 2000))
        assertEquals(0.4f, ReceiptImageSize.scale(4000, 3000))
    }
}
