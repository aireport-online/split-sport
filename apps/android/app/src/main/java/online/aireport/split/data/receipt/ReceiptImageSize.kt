package online.aireport.split.data.receipt

/** How far a receipt photo is scaled down before it is sent: at most 1600 px on the long side. */
object ReceiptImageSize {
    const val MAX_SIDE = 1600

    /** The largest power of two that still leaves the long side at [MAX_SIDE] pixels or more. */
    fun sampleSize(width: Int, height: Int): Int {
        var sample = 1
        while (maxOf(width, height) / (sample * 2) >= MAX_SIDE) sample *= 2
        return sample
    }

    /** The factor that brings the long side down to [MAX_SIDE]; 1 when it is already small enough. */
    fun scale(width: Int, height: Int): Float {
        val longSide = maxOf(width, height)
        return if (longSide <= MAX_SIDE) 1f else MAX_SIDE.toFloat() / longSide
    }
}
