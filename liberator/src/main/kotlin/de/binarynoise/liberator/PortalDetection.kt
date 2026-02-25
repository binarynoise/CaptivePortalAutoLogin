package de.binarynoise.liberator

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

data class PortalTestURL(
    val httpUrl: HttpUrl,
    val httpsUrl: HttpUrl,
) {
    constructor(httpUrl: String, httpsUrl: String) : this(httpUrl.toHttpUrl(), httpsUrl.toHttpUrl())
    constructor(unifiedUrl: HttpUrl) : this(unifiedUrl, unifiedUrl)
    constructor(unifiedUrl: String) : this(unifiedUrl.toHttpUrl())
}

@Suppress("SpellCheckingInspection")
object PortalDetection {
    val backends = mapOf(
        "Binarynoise" to PortalTestURL("http://am-i-captured.binarynoise.de"),
        "Google" to PortalTestURL("http://connectivitycheck.gstatic.com/generate_204", "https://www.google.com/generate_204"),
        "Apple" to PortalTestURL("http://captive.apple.com/hotspot-detect.html"),
        "Microsoft" to PortalTestURL("http://www.msftconnecttest.com/connecttest.txt"),
        "Gnome NetworkManager" to PortalTestURL("http://nmcheck.gnome.org/check_network_status.txt"),
        "KDE" to PortalTestURL("http://networkcheck.kde.org/"),
        "Fedora" to PortalTestURL("http://fedoraproject.org/static/hotspot.txt"),
        "Arch Linux" to PortalTestURL("http://ping.archlinux.org/"),
    )
    
    val defaultBackend: PortalTestURL = backends.entries.first().value
    val defaultBackendKey: String = backends.entries.first().key
    
    val userAgents = mapOf(
        "Chrome/Android" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Mobile Safari/537.36",
        "Firefox/Android" to "Mozilla/5.0 (Android 14; Mobile; rv:130.0) Gecko/129.0 Firefox/130.0",
        
        "Chrome/Windows" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36",
        "Firefox/Windows" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:130.0) Gecko/20100101 Firefox/130.0",
        
        "Chrome/Linux" to "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36",
        "Firefox/Linux" to "Mozilla/5.0 (X11; Linux x86_64; rv:130.0) Gecko/20100101 Firefox/130.0",
        
        "Chrome/iOS" to "Mozilla/5.0 (iPhone; CPU iPhone OS 17_7 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) CriOS/129.0.0.0 Mobile/15E148 Safari/605.1.15",
        "Firefox/iOS" to "Mozilla/5.0 (iPhone; CPU iPhone OS 17_7 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) FxiOS/130.0 Mobile/15E148 Safari/605.1.15",
    )
    val defaultUserAgent = userAgents.entries.first().value
}
