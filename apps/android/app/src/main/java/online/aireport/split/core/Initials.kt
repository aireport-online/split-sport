package online.aireport.split.core

private val WHITESPACE = Regex("\\s+")

/** The first letter of the first two words: "Weekendje Texel" -> "WT", "Anna" -> "A". */
fun initials(name: String): String =
    name.trim()
        .split(WHITESPACE)
        .filter { it.isNotEmpty() }
        .take(2)
        .joinToString("") { word -> word.substring(0, word.offsetByCodePoints(0, 1)).uppercase() }
