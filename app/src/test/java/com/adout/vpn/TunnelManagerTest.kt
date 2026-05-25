package com.adout.vpn

import org.junit.Assert.*
import org.junit.Test

class TunnelManagerTest {

    // ========== DNS Domain Extraction ==========

    @Test
    fun `extractDomain from standard DNS query`() {
        val query = buildDnsQuery("ads.example.com")
        val domain = DnsProtocol.extractDomain(query)
        assertEquals("ads.example.com", domain)
    }

    @Test
    fun `extractDomain handles single-label domain`() {
        val query = buildDnsQuery("localhost")
        val domain = DnsProtocol.extractDomain(query)
        assertEquals("localhost", domain)
    }

    @Test
    fun `extractDomain handles deep subdomain`() {
        val query = buildDnsQuery("a.b.c.d.e.example.com")
        val domain = DnsProtocol.extractDomain(query)
        assertEquals("a.b.c.d.e.example.com", domain)
    }

    @Test
    fun `extractDomain returns null for empty data`() {
        assertNull(DnsProtocol.extractDomain(ByteArray(0)))
    }

    @Test
    fun `extractDomain returns null for data less than DNS header`() {
        assertNull(DnsProtocol.extractDomain(ByteArray(11)))
    }

    @Test
    fun `extractDomain handles random bytes gracefully`() {
        val garbage = ByteArray(100) { it.toByte() }
        val domain = DnsProtocol.extractDomain(garbage)
        // Should not crash, may return null or garbage string
        assertTrue(domain == null || domain.isNotEmpty())
    }

    @Test
    fun `extractDomain handles Chinese domain characters via punycode`() {
        val labels = listOf("xn--fiqs8s", "com")
        val questionData = encodeDnsQuestion(labels)
        val data = ByteArray(12 + questionData.size)
        data[0] = 0x00; data[1] = 0x01
        data[5] = 0x01
        System.arraycopy(questionData, 0, data, 12, questionData.size)
        val domain = DnsProtocol.extractDomain(data)
        assertEquals("xn--fiqs8s.com", domain)
    }

    // ========== Blocked DNS Response ==========

    @Test
    fun `buildBlockedDnsResponse returns valid response for standard query`() {
        val query = buildDnsQuery("ads.example.com")
        val response = DnsProtocol.buildBlockedDnsResponse(query)

        assertNotNull(response)
        assertTrue(response!!.size > query.size)

        // QR bit should be set (response)
        assertTrue((response[2].toInt() and 0x80) != 0)
        // AA bit should be set
        assertTrue((response[2].toInt() and 0x04) != 0)
        // Answer count should be 1
        assertEquals(0x00, response[6].toInt() and 0xFF)
        assertEquals(0x01, response[7].toInt() and 0xFF)
    }

    @Test
    fun `buildBlockedDnsResponse response contains 0_0_0_0 address`() {
        val query = buildDnsQuery("bad.example.com")
        val response = DnsProtocol.buildBlockedDnsResponse(query)
        assertNotNull(response)
        val resp = response!!

        val ip0 = resp[resp.size - 4].toInt() and 0xFF
        val ip1 = resp[resp.size - 3].toInt() and 0xFF
        val ip2 = resp[resp.size - 2].toInt() and 0xFF
        val ip3 = resp[resp.size - 1].toInt() and 0xFF

        assertEquals(0, ip0)
        assertEquals(0, ip1)
        assertEquals(0, ip2)
        assertEquals(0, ip3)
    }

    @Test
    fun `buildBlockedDnsResponse preserves query ID`() {
        val query = buildDnsQuery("example.com")
        query[0] = 0xAB.toByte()
        query[1] = 0xCD.toByte()

        val response = DnsProtocol.buildBlockedDnsResponse(query)
        assertNotNull(response)
        val resp = response!!

        assertEquals(0xAB, resp[0].toInt() and 0xFF)
        assertEquals(0xCD, resp[1].toInt() and 0xFF)
    }

    @Test
    fun `buildBlockedDnsResponse handles short data gracefully`() {
        val shortQuery = ByteArray(5)
        assertNull(DnsProtocol.buildBlockedDnsResponse(shortQuery))
    }

    @Test
    fun `buildBlockedDnsResponse handles zero-length label query`() {
        val query = ByteArray(13)
        query[0] = 0x00
        query[1] = 0x01
        query[5] = 0x01
        val response = DnsProtocol.buildBlockedDnsResponse(query)
        assertNotNull(response)
    }

    @Test
    fun `buildBlockedDnsResponse generates correct answer section`() {
        val query = buildDnsQuery("test.example.com")
        val response = DnsProtocol.buildBlockedDnsResponse(query)
        assertNotNull(response)
        val resp = response!!

        val answerStart = findAnswerOffset(query)

        assertEquals(0xC0.toByte(), resp[answerStart])
        assertEquals(0x0C.toByte(), resp[answerStart + 1])
        assertEquals(0x00.toByte(), resp[answerStart + 2])
        assertEquals(0x01.toByte(), resp[answerStart + 3])
        assertEquals(0x00.toByte(), resp[answerStart + 4])
        assertEquals(0x01.toByte(), resp[answerStart + 5])

        // TTL = 4 bytes big-endian: 0x00 0x00 0x00 0x3C = 60
        assertEquals(0x00.toByte(), resp[answerStart + 6])
        assertEquals(0x00.toByte(), resp[answerStart + 7])
        assertEquals(0x00.toByte(), resp[answerStart + 8])
        assertEquals(0x3C.toByte(), resp[answerStart + 9])

        // IP 0.0.0.0 at bytes 12-15
        assertEquals(0x00.toByte(), resp[answerStart + 12])
        assertEquals(0x00.toByte(), resp[answerStart + 13])
        assertEquals(0x00.toByte(), resp[answerStart + 14])
        assertEquals(0x00.toByte(), resp[answerStart + 15])
    }

    // ========== IP Checksum ==========

    @Test
    fun `computeIpChecksum for standard IPv4 header`() {
        val header = byteArrayOf(
            0x45, 0x00, 0x00, 0x3C.toByte(),
            0x00, 0x00, 0x00, 0x00,
            0x40, 0x11, 0x00, 0x00,
            0x0A.toByte(), 0x00, 0x00, 0x02,
            0x0A.toByte(), 0x00, 0x00, 0x01
        )
        val checksum = DnsProtocol.computeIpChecksum(header)
        assertTrue(checksum in 0..0xFFFF)
        assertTrue(checksum != 0)

        // Verify: set checksum and recompute should give 0
        header[10] = (checksum shr 8).toByte()
        header[11] = (checksum and 0xFF).toByte()
        assertEquals(0, DnsProtocol.computeIpChecksum(header))
    }

    @Test
    fun `computeIpChecksum for zeroed header is 0xFFFF`() {
        assertEquals(0xFFFF, DnsProtocol.computeIpChecksum(ByteArray(20)))
    }

    @Test
    fun `computeIpChecksum for odd-length header`() {
        val checksum = DnsProtocol.computeIpChecksum(ByteArray(21))
        assertTrue(checksum in 0..0xFFFF)
    }

    @Test
    fun `computeIpChecksum typical DNS response header`() {
        // Typical IP header: 10.0.0.2 -> 8.8.8.8, UDP proto
        val header = byteArrayOf(
            0x45, 0x00, 0x00, 0x3C.toByte(), // ver/ihl, dscp, total len
            0x12, 0x34, 0x40, 0x00,            // id, flags/frag
            0x40, 0x11, 0x00, 0x00,            // TTL, proto(UDP=17), checksum=0
            0x0A.toByte(), 0x00, 0x00, 0x02,  // src = 10.0.0.2
            0x08, 0x08, 0x08, 0x08            // dst = 8.8.8.8
        )
        val checksum = DnsProtocol.computeIpChecksum(header)

        // Set it and verify
        header[10] = (checksum shr 8).toByte()
        header[11] = (checksum and 0xFF).toByte()
        assertEquals(0, DnsProtocol.computeIpChecksum(header))
    }

    // ========== DNS Response Packet ==========

    @Test
    fun `buildDnsResponsePacketFromRaw produces valid packet structure`() {
        val ipHeader = byteArrayOf(
            0x45, 0x00, 0x00, 0x3C.toByte(),
            0x00, 0x00, 0x40, 0x00,
            0x40, 0x11, 0x00, 0x00,
            0x0A.toByte(), 0x00, 0x00, 0x02,
            0x08, 0x08, 0x08, 0x08
        )
        val udpHeader = byteArrayOf(
            0x00, 0x35, 0x00, 0x35,
            0x00, 0x1C.toByte(), 0x00, 0x00
        )
        val dnsResponseData = ByteArray(20) { it.toByte() }
        val originalPacket = ipHeader + udpHeader + dnsResponseData

        val responsePacket = DnsProtocol.buildDnsResponsePacketFromRaw(
            originalPacket, dnsResponseData, ipHeader.size
        )

        assertEquals(ipHeader.size + 8 + dnsResponseData.size, responsePacket.size)

        // IP src/dst swapped (original dst=8.8.8.8 -> becomes src; original src=10.0.0.2 -> becomes dst)
        assertEquals(8, responsePacket[12].toInt() and 0xFF)
        assertEquals(8, responsePacket[13].toInt() and 0xFF)
        assertEquals(10, responsePacket[16].toInt() and 0xFF)
        assertEquals(0, responsePacket[17].toInt() and 0xFF)

        // UDP source port 53
        assertEquals(0, responsePacket[20].toInt() and 0xFF)
        assertEquals(53, responsePacket[21].toInt() and 0xFF)

        // DNS data at end
        assertEquals(dnsResponseData.size, responsePacket.size - ipHeader.size - 8)
    }

    @Test
    fun `buildDnsResponsePacketFromRaw checksum is valid`() {
        val ipHeader = byteArrayOf(
            0x45, 0x00, 0x00, 0x3C.toByte(),
            0x00, 0x00, 0x40, 0x00,
            0x40, 0x11, 0x00, 0x00,
            0x0A.toByte(), 0x00, 0x00, 0x02,
            0x08, 0x08, 0x08, 0x08
        )
        val udpHeader = ByteArray(8)
        val dnsData = ByteArray(16)
        val originalPacket = ipHeader + udpHeader + dnsData

        val packet = DnsProtocol.buildDnsResponsePacketFromRaw(originalPacket, dnsData, ipHeader.size)

        // Extract IP header from packet and verify checksum
        val resultIpHeader = packet.copyOf(ipHeader.size)
        assertEquals(0, DnsProtocol.computeIpChecksum(resultIpHeader))
    }

    // ========== DNS Protocol Integration ==========

    @Test
    fun `blocked domain query produces valid response packet`() {
        val domain = "tracker.example.com"
        val query = buildDnsQuery(domain)

        val dnsResponseOrNull = DnsProtocol.buildBlockedDnsResponse(query)
        assertNotNull(dnsResponseOrNull)
        val dnsResponse = dnsResponseOrNull!!

        // Build a minimal IP+UDP header to test end-to-end
        val ipHeader = byteArrayOf(
            0x45, 0x00, 0x00, 0x3C.toByte(),
            0x00, 0x00, 0x40, 0x00,
            0x40, 0x11, 0x00, 0x00,
            0x0A.toByte(), 0x00, 0x00, 0x02, // src
            0x08, 0x08, 0x08, 0x08           // dst
        )
        val udpHeader = byteArrayOf(0x04, 0x00, 0x00, 0x35, 0x00, 0x1C.toByte(), 0x00, 0x00)
        val originalPacket = ipHeader + udpHeader + query

        val responsePacket = DnsProtocol.buildDnsResponsePacketFromRaw(originalPacket, dnsResponse, ipHeader.size)

        // Verify complete packet structure
        assertTrue(responsePacket.size > originalPacket.size)

        // Verify IP checksum
        val ip = responsePacket.copyOf(ipHeader.size)
        assertEquals(0, DnsProtocol.computeIpChecksum(ip))

        // Verify DNS response has blocked IP
        val dnsOffset = ipHeader.size + 8
        val respData = responsePacket.copyOfRange(dnsOffset, responsePacket.size)
        assertEquals(dnsResponse.size, respData.size)
    }

    // ========== Domain boundary validation ==========

    @Test
    fun `extractDomain with compression pointer terminates correctly`() {
        // DNS with compressed name (pointer only)
        val data = ByteArray(14)
        data[0] = 0x12; data[1] = 0x34
        data[5] = 0x01
        data[12] = 0xC0.toByte() // pointer
        data[13] = 0x0C

        val domain = DnsProtocol.extractDomain(data)
        assertNull(domain) // pointer with no prior labels -> null from empty parts
    }

    // ========== Helpers ==========

    private fun buildDnsQuery(domain: String): ByteArray {
        val labels = domain.split(".")
        val questionData = encodeDnsQuestion(labels)
        val packet = ByteArray(12 + questionData.size)
        packet[0] = 0x00; packet[1] = 0x01
        packet[2] = 0x01; packet[5] = 0x01
        System.arraycopy(questionData, 0, packet, 12, questionData.size)
        return packet
    }

    private fun encodeDnsQuestion(labels: List<String>): ByteArray {
        val data = java.io.ByteArrayOutputStream()
        for (label in labels) {
            val bytes = label.toByteArray()
            data.write(bytes.size)
            data.write(bytes)
        }
        data.write(0)
        data.write(0x00); data.write(0x01) // QTYPE A
        data.write(0x00); data.write(0x01) // QCLASS IN
        return data.toByteArray()
    }

    private fun findAnswerOffset(query: ByteArray): Int {
        var offset = 12
        while (offset < query.size && query[offset].toInt() != 0) {
            val len = query[offset].toInt() and 0xFF
            if (len and 0xC0 == 0xC0) { offset += 2; break }
            offset += len + 1
        }
        if (offset < query.size && query[offset].toInt() == 0) offset++
        return offset + 4
    }
}
