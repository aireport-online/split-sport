package online.aireport.split.core

/** Invite codes are 8 characters from A–Z and 2–9, case-insensitive. */
object InviteCode {
    const val LENGTH = 8
    private val VALID = Regex("^[A-Z2-9]{$LENGTH}$")
    private val JOIN_PATH = Regex("/join/([A-Za-z2-9]{$LENGTH})/?$")

    /** Uppercases what was typed and drops everything that cannot be part of a code. */
    fun sanitize(input: String): String =
        input.uppercase().filter { it in 'A'..'Z' || it in '2'..'9' }.take(LENGTH)

    fun isValid(code: String): Boolean = VALID.matches(code)

    /**
     * Finds the code in a share link (`https://server/join/CODE` or `split://join/CODE`) or in a
     * bare code. Returns null when there is none.
     */
    fun fromLink(link: String): String? {
        val trimmed = link.trim()
        // `split://join/CODE` parses "join" as the host, so match on the text rather than a path.
        val fromPath = JOIN_PATH.find(trimmed.substringBefore('?').substringBefore('#'))
            ?.groupValues?.get(1)?.uppercase()
        return fromPath ?: trimmed.uppercase().takeIf(::isValid)
    }

    fun shareLink(serverUrl: String, code: String): String = "${serverUrl.trimEnd('/')}/join/$code"
}
