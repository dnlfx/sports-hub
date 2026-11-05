package com.sportshub.app.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import java.net.URI

/**
 * Android 17 (API 37) asks the user before an app connects to devices on their home
 * network. Sports Hub only needs that for a source the user saved on such a device,
 * like a media server, so it asks only then.
 */
object LocalNetwork {
    const val PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"

    /** Android 17. */
    private const val FIRST_SDK = 37

    fun needsPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT >= FIRST_SDK &&
            ContextCompat.checkSelfPermission(context, PERMISSION) != PackageManager.PERMISSION_GRANTED

    /**
     * Whether [url] points at the local network: a private or link-local IP address, or a
     * name that only resolves at home (".local", ".lan", ".home.arpa"). No DNS lookups.
     */
    fun isLocalAddress(url: String): Boolean {
        val host = runCatching { URI(url).host }.getOrNull()?.lowercase()?.trim('[', ']') ?: return false
        if (host.endsWith(".local") || host.endsWith(".lan") || host.endsWith(".home.arpa")) return true
        if (':' in host) return host.startsWith("fe80:") || host.startsWith("fc") || host.startsWith("fd")
        val parts = host.split('.').map { it.toIntOrNull() ?: return false }
        if (parts.size != 4 || parts.any { it !in 0..255 }) return false
        val (a, b) = parts
        return a == 10 ||
            (a == 172 && b in 16..31) ||
            (a == 192 && b == 168) ||
            (a == 169 && b == 254)
    }
}
