package com.adout.vpn

import org.junit.Assert.*
import org.junit.Test

class DnsProtocolTest {

    @Test
    fun `buildEmptyDnsResponse creates valid empty response`() {
        val query = byteArrayOf(
            0x00, 0x01,  // Transaction ID
            0x01, 0x00,  // Flags
            0x00, 0x01,  // Questions
            0x00, 0x00,  // Answers
            0x00, 0x00,  // Authority
            0x00, 0x00,  // Additional
            // Query: example.com
            0x07, 'e'.code.toByte(), 'x'.code.toByte(), 'a'.code.toByte(),
            'm'.code.toByte(), 'p'.code.toByte(), 'l'.code.toByte(), 'e'.code.toByte(),
            0x03, 'c'.code.toByte(), 'o'.code.toByte(), 'm'.code.toByte(),
            0x00,
            0x00, 0x01,  // Type A
            0x00, 0x01   // Class IN
        )
        val response = DnsProtocol.buildEmptyDnsResponse(query)
        assertNotNull(response)
        // Check QR bit
        assertTrue(response!![2].toInt() and 0x80 != 0)
        // Check Answer Count = 0
        assertEquals(0, response[6].toInt())
        assertEquals(0, response[7].toInt())
    }
}
