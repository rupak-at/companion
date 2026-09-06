package com.ambientcompanion.download.classifier

import java.net.URI

data class ClassifiedLink(val normalizedUrl: String, val type: LinkType, val providerName: String)

object LinkClassifier {
    private val videoExtensions = setOf("mp4", "webm", "mov")
    private val imageExtensions = setOf("jpg", "jpeg", "png", "webp")

    fun classify(input: String): ClassifiedLink? {
        val normalized = input.trim().trim('"', '\'')
        val uri = runCatching { URI(normalized) }.getOrNull() ?: return null
        if (!uri.scheme.equals("https", ignoreCase = true) || uri.userInfo != null || uri.host == null) return null

        val host = uri.host.lowercase().trimEnd('.')
        val pathExtension = uri.path.substringAfterLast('.', "").lowercase()
        val (type, name) = when {
            host.isDomainOrSubdomainOf("tiktok.com") -> LinkType.TIKTOK to "TikTok"
            host.isDomainOrSubdomainOf("instagram.com") -> LinkType.INSTAGRAM to "Instagram"
            host.isDomainOrSubdomainOf("facebook.com") || host.isDomainOrSubdomainOf("fb.watch") ->
                LinkType.FACEBOOK to "Facebook"
            pathExtension in videoExtensions -> LinkType.DIRECT_VIDEO to "Direct video"
            pathExtension in imageExtensions -> LinkType.DIRECT_IMAGE to "Direct image"
            else -> LinkType.UNKNOWN to "Unsupported link"
        }
        return ClassifiedLink(uri.toASCIIString(), type, name)
    }

    private fun String.isDomainOrSubdomainOf(domain: String): Boolean = this == domain || endsWith(".$domain")
}
