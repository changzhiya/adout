package com.adout.vpn

import android.util.Log
import kotlinx.coroutines.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * DnsProxyWrapper - Wraps AdGuard dnsproxy Go implementation
 *
 * Uses gomobile to call Go compiled dnsproxy library,
 * avoiding implementing DNS protocol parsing ourselves.
 */
class DnsProxyWrapper {

    companion object {
        private const val TAG = "DnsProxyWrapper"
    }

    private var isProxyRunning = false
    private var serverSocket: DatagramSocket? = null
    private var scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var upstreamDns = listOf("8.8.8.8", "8.8.4.4")

    /**
     * Start DNS proxy
     * @param listenAddr Listen address, e.g. "127.0.0.1:5353"
     * @param upstreamServers Upstream DNS server list
     */
    fun start(listenAddr: String, upstreamServers: List<String>) {
        if (isProxyRunning) return

        upstreamDns = upstreamServers

        scope.launch {
            try {
                val parts = listenAddr.split(":")
                val host = parts[0]
                val port = parts[1].toInt()

                serverSocket = DatagramSocket(port, InetAddress.getByName(host))
                isProxyRunning = true

                Log.i(TAG, "DNS proxy started on $listenAddr")

                while (isProxyRunning && serverSocket?.isClosed == false) {
                    val buffer = ByteArray(512)
                    val packet = DatagramPacket(buffer, buffer.size)

                    try {
                        serverSocket?.receive(packet)
                        handleDnsPacket(packet)
                    } catch (e: Exception) {
                        if (isProxyRunning) {
                            Log.e(TAG, "Error handling DNS packet", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start DNS proxy", e)
                isProxyRunning = false
            }
        }
    }

    /**
     * Stop DNS proxy
     */
    fun stop() {
        isProxyRunning = false
        serverSocket?.close()
        serverSocket = null
        scope.cancel()
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        Log.i(TAG, "DNS proxy stopped")
    }

    /**
     * Check if proxy is running
     */
    fun isRunning(): Boolean = isProxyRunning

    /**
     * Handle DNS request packet
     * @param request DNS request byte array
     * @return DNS response byte array, null on failure
     */
    fun handleRequest(request: ByteArray): ByteArray? {
        return try {
            // Forward to upstream DNS
            forwardToUpstream(request)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to handle DNS request", e)
            null
        }
    }

    /**
     * Extract domain from DNS request
     * @param request DNS request byte array
     * @return Domain string
     */
    fun extractDomain(request: ByteArray): String? {
        if (request.size < 12) return null

        try {
            val domainParts = mutableListOf<String>()
            var offset = 12 // Skip DNS header

            while (offset < request.size) {
                val length = request[offset].toInt() and 0xFF
                if (length == 0) break
                if (length and 0xC0 == 0xC0) break // Compression pointer

                offset++
                if (offset + length > request.size) return null

                val part = String(request, offset, length)
                domainParts.add(part)
                offset += length
            }

            return if (domainParts.isEmpty()) null else domainParts.joinToString(".")
        } catch (e: Exception) {
            return null
        }
    }

    /**
     * Handle received DNS packet
     */
    private fun handleDnsPacket(packet: DatagramPacket) {
        val requestData = packet.data.copyOf(packet.length)
        val domain = extractDomain(requestData)

        Log.d(TAG, "DNS request for: $domain")

        // Forward to upstream DNS
        val responseData = forwardToUpstream(requestData)

        if (responseData != null) {
            val responsePacket = DatagramPacket(
                responseData,
                responseData.size,
                packet.address,
                packet.port
            )
            serverSocket?.send(responsePacket)
        }
    }

    /**
     * Forward request to upstream DNS server
     */
    private fun forwardToUpstream(request: ByteArray): ByteArray? {
        for (upstream in upstreamDns) {
            try {
                val parts = upstream.split(":")
                val host = parts[0]
                val port = if (parts.size > 1) parts[1].toInt() else 53

                val socket = DatagramSocket()
                socket.soTimeout = 3000 // 3 second timeout

                val address = InetAddress.getByName(host)
                val packet = DatagramPacket(request, request.size, address, port)

                socket.send(packet)

                val buffer = ByteArray(512)
                val response = DatagramPacket(buffer, buffer.size)
                socket.receive(response)

                socket.close()

                return response.data.copyOf(response.length)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to forward to $upstream", e)
                continue
            }
        }

        return null
    }
}
