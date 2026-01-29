package de.binarynoise.captiveportalautologin.portalproxy.portal
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.UnknownHostException
import io.vertx.core.http.HttpServerRequest

fun HttpServerRequest.getRealRemoteIP(): String {
    val ip = remoteAddress().host()
    
    val headers = headers()
    if (isLanIp(ip) && "X-Real-IP" in headers) {
        val realIp = headers["X-Real-IP"]
        if (realIp != null) return realIp.split(",").first().trim()
    }
    return ip
}

fun isLanIp(ipString: String): Boolean {
    val addr = parseIp(ipString) ?: return false
    
    return when (addr) {
        is Inet4Address -> isPrivateIPv4(addr)
        is Inet6Address -> isLocalIPv6(addr)
        else -> false
    }
}

fun parseIp(ipString: String): InetAddress? = try {
    InetAddress.getByName(ipString.trim())
} catch (_: UnknownHostException) {
    null
} catch (_: SecurityException) {
    null
}


val loopbackV6 = ByteArray(16) { if (it == 15) 1 else 0 }
fun isLocalIPv6(addr: Inet6Address): Boolean {
    val bytes = addr.address
    val b0 = bytes[0].toInt()
    val b1 = bytes[1].toInt()
    
    // fc00::/7 (1111 110x)
    val isUniqueLocal = (b0 and 0xFE) == 0xFC
    // fe80::/10 (1111 1110 10xx xxxx)
    val isLinkLocal = (b0 == 0xFE) && ((b1 and 0xC0) == 0x80)
    val isLoopback = bytes.contentEquals(loopbackV6)
    
    return isUniqueLocal || isLinkLocal || isLoopback
}

fun isPrivateIPv4(addr: Inet4Address): Boolean {
    val bytes = addr.address
    val b0 = bytes[0].toInt() and 0xFF
    val b1 = bytes[1].toInt() and 0xFF
    
    return when (b0) {
        // 10.0.0.0/8
        10 -> true
        // 127.0.0.1/8
        127 -> true
        // 172.16.0.0/12
        172 if (b1 in 16..31) -> true
        // 192.168.0.0/16
        192 if b1 == 168 -> true
        else -> false
    }
}
