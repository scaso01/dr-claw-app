package com.scaso.drclawapp.data.websocket

import okhttp3.Dns
import java.net.InetAddress

/**
 * Holds the Meshnet IP the gateway hostname is pinned to. Mutable at runtime so a
 * Settings change takes effect on the next reconnect without rebuilding the OkHttp client.
 *
 * No Android imports -- KMP-extractable.
 */
class MeshnetIpHolder(initial: String) {
    @Volatile
    var ip: String = initial
}

/**
 * IP-first, DNS-fallback resolver for the Meshnet path.
 *
 * The one fragile link on the phone -> Meshnet -> docker-host -> workstation path is DNS:
 * NordVPN silently ignores the phone's resolver when routing over Meshnet, so a
 * `gateway.example.com` lookup can die even though the tunnel is up. This resolver
 * returns the pinned Meshnet IP FIRST (a numeric literal, so no DNS call), then appends
 * whatever the system resolver returns as a fallback. OkHttp tries the addresses in order.
 *
 * The hostname (and thus SNI + the certificate-pinned host) is unchanged, so TLS and
 * certificate pinning still pass -- this is why we pin via a custom Dns instead of a
 * bare-IP URL (which would fail TLS hostname verification against Caddy's cert).
 *
 * No Android imports -- KMP-extractable.
 */
class MeshnetFirstDns(
    private val pinnedHost: String,
    private val ipSupplier: () -> String,
    private val system: Dns = Dns.SYSTEM,
) : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val ip = ipSupplier().trim()
        if (pinnedHost.isBlank() || hostname != pinnedHost || ip.isEmpty()) {
            return system.lookup(hostname)
        }
        // getByName on a numeric literal parses it -- no DNS lookup. Falls through to
        // system DNS if the configured IP is somehow unparseable.
        val pinned = runCatching { InetAddress.getByName(ip) }.getOrNull()
            ?: return system.lookup(hostname)
        val rest = runCatching { system.lookup(hostname) }.getOrDefault(emptyList())
        return (listOf(pinned) + rest).distinct()
    }
}
