package com.ambientcompanion.download.share

private val httpsUrl = Regex("https://[^\\s<>\\\"]+", RegexOption.IGNORE_CASE)

object SharedUrlExtractor {
    fun extract(text: String?): String? = text
        ?.let(httpsUrl::find)
        ?.value
        ?.trimEnd('.', ',', ';', ')', ']', '}')
}
