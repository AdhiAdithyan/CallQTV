package com.softland.callqtv.viewmodel

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class KeypadPayloadParserTest {

    @Test
    fun extracts_serial_from_latest_payload_format_samples() {
        assertEquals("AdCAL0k007", KeypadPayloadParser.extractKeypadSerial("$0Je-AdCAL0k0071010001*"))
        assertEquals("AdCAL0k007", KeypadPayloadParser.extractKeypadSerial("$0Je-AdCAL0k0071010002*"))
        assertEquals("AdCAL0k007", KeypadPayloadParser.extractKeypadSerial("$0Jh-AdCAL0k0071010003*"))
    }

    @Test
    fun extracts_serial_from_legacy_payload_format() {
        assertEquals("2026bCAL0K0007", KeypadPayloadParser.extractKeypadSerial("$02026bCAL0K00071100030*"))
    }

    @Test
    fun returns_null_for_invalid_payload_wrappers() {
        assertNull(KeypadPayloadParser.extractKeypadSerial("0Je-AdCAL0k0071010001*"))
        assertNull(KeypadPayloadParser.extractKeypadSerial("$0Je-AdCAL0k0071010001"))
        assertNull(KeypadPayloadParser.extractKeypadSerial(""))
    }
}
