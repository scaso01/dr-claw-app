package com.scaso.drclawapp.data.websocket

import okhttp3.Dns
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

class MeshnetFirstDnsTest {

    private val host = "gateway.example.com"

    // Fake system resolver: returns fixed numeric addresses (no real DNS / network).
    private fun fakeSystem(vararg ips: String) = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> = ips.map { InetAddress.getByName(it) }
    }

    @Test
    fun `pinned host puts meshnet ip first, system addrs appended`() {
        val dns = MeshnetFirstDns(host, { "198.51.100.41" }, fakeSystem("203.0.113.9"))
        val result = dns.lookup(host)
        assertEquals(InetAddress.getByName("198.51.100.41"), result[0])
        assertTrue(result.contains(InetAddress.getByName("203.0.113.9")))
        assertEquals(2, result.size)
    }

    @Test
    fun `dedupes when system resolves to the same meshnet ip`() {
        val dns = MeshnetFirstDns(host, { "198.51.100.41" }, fakeSystem("198.51.100.41"))
        val result = dns.lookup(host)
        assertEquals(1, result.size)
        assertEquals(InetAddress.getByName("198.51.100.41"), result[0])
    }

    @Test
    fun `non-pinned host delegates to system unchanged`() {
        val dns = MeshnetFirstDns(host, { "198.51.100.41" }, fakeSystem("198.51.100.5"))
        assertEquals(listOf(InetAddress.getByName("198.51.100.5")), dns.lookup("example.com"))
    }

    @Test
    fun `blank ip delegates to system (no crash)`() {
        val dns = MeshnetFirstDns(host, { "" }, fakeSystem("198.51.100.5"))
        assertEquals(listOf(InetAddress.getByName("198.51.100.5")), dns.lookup(host))
    }

    @Test
    fun `blank pinned host delegates to system`() {
        val dns = MeshnetFirstDns("", { "198.51.100.41" }, fakeSystem("198.51.100.5"))
        assertEquals(listOf(InetAddress.getByName("198.51.100.5")), dns.lookup(host))
    }

    @Test
    fun `ip is read fresh on each lookup (Settings change applies on reconnect)`() {
        var ip = "198.51.100.41"
        val dns = MeshnetFirstDns(host, { ip }, fakeSystem("203.0.113.9"))
        assertEquals(InetAddress.getByName("198.51.100.41"), dns.lookup(host)[0])
        ip = "100.99.99.99"
        assertEquals(InetAddress.getByName("100.99.99.99"), dns.lookup(host)[0])
    }
}
