package net.djvk.fireflyPlaidConnector2.util

object Utilities {
    fun getRandomAlphabeticalString(length: Int) : String {
        val allowedChars = ('A'..'Z') + ('a'..'z')
        return (1..length)
            .map { allowedChars.random() }
            .joinToString("")
    }
}