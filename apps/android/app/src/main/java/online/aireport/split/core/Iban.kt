package online.aireport.split.core

/** Groups an IBAN in fours for reading: "NL91ABNA0417164300" -> "NL91 ABNA 0417 1643 00". */
fun formatIban(iban: String): String = iban.filterNot { it.isWhitespace() }.chunked(4).joinToString(" ")
