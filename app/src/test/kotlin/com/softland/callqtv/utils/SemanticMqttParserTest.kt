package com.softland.callqtv.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SemanticMqttParserTest {

    @Test
    fun parse_fixed_payload_treats_dash_type_as_normal_token() {
        val payload = "$0PA-AeCAL0K0001lo-0008*"

        val parsed = SemanticMqttParser.parseFixedPayload(payload)

        assertNotNull(parsed)
        assertEquals("AeCAL0K0001", parsed.serial)
        assertEquals("8", parsed.token)
        assertEquals(SemanticMqttParser.PayloadAction.NORMAL, parsed.action)
        assertEquals(false, parsed.isVipEmergency)
    }

    @Test
    fun parse_fixed_payload_treats_d_type_as_normal_token() {
        val payload = "$0PADAeCAL0K0001lo-0010*"

        val parsed = SemanticMqttParser.parseFixedPayload(payload)

        assertNotNull(parsed)
        assertEquals("AeCAL0K0001", parsed.serial)
        assertEquals("10", parsed.token)
        assertEquals(SemanticMqttParser.PayloadAction.NORMAL, parsed.action)
        assertEquals(true, parsed.isVipEmergency)
    }

    @Test
    fun parse_fixed_payload_treats_b_type_as_db_only_transferred() {
        // Same frame as dash test but index 4 is `B` (transferred token — no UI channel).
        val payload = "$0PABAeCAL0K0001lo-0008*"

        val parsed = SemanticMqttParser.parseFixedPayload(payload)

        assertNotNull(parsed)
        assertEquals("AeCAL0K0001", parsed.serial)
        assertEquals("8", parsed.token)
        assertEquals(SemanticMqttParser.PayloadAction.DB_ONLY, parsed.action)
        assertEquals(false, parsed.isVipEmergency)
    }

    @Test
    fun parse_fixed_payload_user_sample_b_transferred_is_db_only() {
        val payload = "$0OsBAbCAL0K000333-0005*"

        val parsed = SemanticMqttParser.parseFixedPayload(payload)

        assertNotNull(parsed)
        assertEquals("AbCAL0K0003", parsed.serial)
        assertEquals("5", parsed.token)
        assertEquals(SemanticMqttParser.PayloadAction.DB_ONLY, parsed.action)
        assertEquals(false, parsed.isVipEmergency)
    }
}
