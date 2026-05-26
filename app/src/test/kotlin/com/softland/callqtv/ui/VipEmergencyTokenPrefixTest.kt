package com.softland.callqtv.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VipEmergencyTokenPrefixTest {

    @Test
    fun vipEmergencyPrefixAppliesToAnySlotTokenInSet() {
        val vipTokens = setOf("42", "99")
        assertTrue(tokenUsesVipEmergencyPrefix("42", vipTokens))
        assertTrue(tokenUsesVipEmergencyPrefix(" 99 ", vipTokens))
        assertFalse(tokenUsesVipEmergencyPrefix("12", vipTokens))
    }

    @Test
    fun emptyOrBlankTokenIsNotVip() {
        assertFalse(tokenUsesVipEmergencyPrefix(null, setOf("1")))
        assertFalse(tokenUsesVipEmergencyPrefix("", setOf("1")))
        assertFalse(tokenUsesVipEmergencyPrefix("  ", setOf("1")))
    }
}
