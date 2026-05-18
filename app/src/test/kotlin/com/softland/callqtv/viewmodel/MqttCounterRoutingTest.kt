package com.softland.callqtv.viewmodel

import com.softland.callqtv.data.local.CounterEntity
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MqttCounterRoutingTest {

    @Test
    fun findCounterEntity_matches_button_index() {
        val counters = listOf(
            counter(buttonIndex = 2, keypadIndex = "5"),
            counter(buttonIndex = 1, keypadIndex = "1"),
        )

        assertEquals(1, findCounterEntityForMqttRoute(counters, "1")?.buttonIndex)
    }

    @Test
    fun findCounterEntity_falls_back_to_keypad_index_when_button_index_differs() {
        val counters = listOf(
            counter(buttonIndex = 2, keypadIndex = "7"),
        )

        assertEquals(2, findCounterEntityForMqttRoute(counters, "7")?.buttonIndex)
    }

    @Test
    fun findCounterEntity_returns_null_for_unknown_route() {
        val counters = listOf(counter(buttonIndex = 1, keypadIndex = "1"))

        assertNull(findCounterEntityForMqttRoute(counters, "99"))
    }

    private fun counter(buttonIndex: Int, keypadIndex: String) = CounterEntity(
        deviceId = 1,
        macAddress = "aa:bb",
        customerId = "0001",
        counterId = "c$buttonIndex",
        defaultName = null,
        defaultCode = null,
        keypadIndex = keypadIndex,
        dispenserIndex = null,
        sourceDevice = null,
        buttonIndex = buttonIndex,
        dispenserButtonNumber = null,
        name = null,
        code = null,
        rowSpan = null,
        colSpan = null,
        isEnabled = true,
        audioUrl = null,
        audioName = null,
        counterConfigId = null,
        maxTokenNumber = null,
        dispenserSerialNumber = null,
        dispenserTokenType = null,
        dispenserDisplayName = null,
        rawJson = null,
    )
}
