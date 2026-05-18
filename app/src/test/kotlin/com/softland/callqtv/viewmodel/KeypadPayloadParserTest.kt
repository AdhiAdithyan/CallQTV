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
    fun extracts_serial_from_fixed_protocol_with_dash_type() {
        assertEquals("AbZXCV90001", KeypadPayloadParser.extractKeypadSerial("$0MP-AbZXCV9000111-0004*"))
    }

    @Test
    fun extracts_serial_from_clr_payload_format() {
        assertEquals("AbCAL0K0001", KeypadPayloadParser.extractKeypadSerial("$000-AbCAL0K000101CLR0*"))
    }

    @Test
    fun extracts_clear_payload_info_from_clr_payload_format() {
        val info = KeypadPayloadParser.extractClearPayloadInfo("$000-AbCAL0K000101CLR0*")

        assertEquals("AbCAL0K0001", info?.serial)
        assertEquals("1", info?.routeIndex)
    }

    /** Keypad SN is 11 chars `AbCAL0K0003`; `303` is token digits `03` + last route digit `3` before CLR. */
    @Test
    fun extracts_serial_and_clear_info_when_token_has_three_digits_before_clr() {
        val payload = "\$000-AbCAL0K000303CLR0*"
        assertEquals("AbCAL0K0003", KeypadPayloadParser.extractKeypadSerial(payload))
        val info = KeypadPayloadParser.extractClearPayloadInfo(payload)
        assertEquals("AbCAL0K0003", info?.serial)
        assertEquals("3", info?.routeIndex)
    }

    @Test
    fun extracts_serial_from_legacy_payload_format() {
        assertEquals("2026bCAL0K0007", KeypadPayloadParser.extractKeypadSerial("$02026bCAL0K00071100030*"))
    }

    @Test
    fun extracts_serial_from_nv_dash_payload_without_using_trailing_serial_digit_as_route() {
        val payload = "\$0NV-AbCAL0K000625-0002*"
        assertEquals("AbCAL0K0006", KeypadPayloadParser.extractKeypadSerial(payload))
        val fixed = com.softland.callqtv.utils.SemanticMqttParser.parseFixedPayload(payload)
        assertEquals("AbCAL0K0006", fixed?.serial)
        assertEquals("2", fixed?.token)
    }

    @Test
    fun returns_null_for_invalid_payload_wrappers() {
        assertNull(KeypadPayloadParser.extractKeypadSerial("0Je-AdCAL0k0071010001*"))
        assertNull(KeypadPayloadParser.extractKeypadSerial("$0Je-AdCAL0k0071010001"))
        assertNull(KeypadPayloadParser.extractKeypadSerial(""))
    }
}
