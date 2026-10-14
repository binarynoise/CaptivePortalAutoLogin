package de.binarynoise.captiveportalautologin.portalproxy

data class PortalProxyConfig(
    val proxyPort: Int = 8000,
    val portalPort: Int = 8001,
    val portalHost: String? = null,
    val allowlistDomain: List<String>? = null,
    val allowlistPort: List<String>? = null,
) {
    companion object {
        fun fromEnvironment(): PortalProxyConfig = PortalProxyConfig(
            proxyPort = System.getenv("PROXY_PORT")?.toInt() ?: 8000,
            portalPort = System.getenv("PORTAL_PORT")?.toInt() ?: 8001,
            portalHost = System.getenv("PORTAL_HOST"),
            allowlistDomain = System.getenv("PROXY_ALLOWLIST_DOMAIN")?.split(",")?.map { it.trim() },
            allowlistPort = System.getenv("PROXY_ALLOWLIST_PORT")?.split(",")?.map { it.trim() },
        )
    }
}
