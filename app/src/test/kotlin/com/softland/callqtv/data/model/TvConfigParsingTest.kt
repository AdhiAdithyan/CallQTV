package com.softland.callqtv.data.model

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TvConfigParsingTest {

    private val gson = Gson()

    @Test
    fun parsesCounterStringIndicesAndDispenserSn() {
        val json = """
            {
              "status": "success",
              "device_id": 4,
              "tv_config": { "layout_type": "full", "token_format": "T2" },
              "connected_devices": [
                {
                  "id": 33,
                  "device_type": "BROKER",
                  "config": { "host": "192.168.0.107", "port": "1883", "topic": "test-topic" }
                }
              ],
              "counters": [
                {
                  "counter_id": "3",
                  "keypad_index": "1",
                  "dispenser_index": "1",
                  "button_index": "1",
                  "dispenser_button_index": "1",
                  "dispenser_sn": "2026bCAL0D0007",
                  "is_enabled": true
                }
              ],
              "scroll_config": {
                "scroll_enabled": "on",
                "scroll_text_lines": ["Hello"]
              }
            }
        """.trimIndent()

        val response = gson.fromJson(json, TvConfigResponse::class.java)
        assertEquals("success", response.status)
        assertEquals(4, response.deviceId)

        val counter = response.counters?.single()
        assertNotNull(counter)
        assertEquals(1, counter!!.dispenserIndex)
        assertEquals(1, counter.buttonIndex)
        assertEquals(1, counter.dispenserButtonIndex)
        assertEquals("2026bCAL0D0007", counter.dispenserSn)

        assertEquals("full", response.tvConfig?.layoutType)
        assertEquals("T2", response.tvConfig?.tokenFormat)
        assertEquals("on", response.scrollConfig?.scrollEnabled)
        assertEquals(listOf("Hello"), response.scrollConfig?.scrollTextLines)

        val broker = response.connectedDevices?.first()
        assertEquals("BROKER", broker?.deviceType)
        assertNotNull(broker?.config)
    }

    @Test
    fun parsesProductionStyleTvConfigResponse() {
        val json = """
            {
              "status": "success",
              "message": "TV configuration fetched successfully",
              "device_id": 10,
              "serial_number": "e8434c7e0ce29b50",
              "company_name": "GH ERNAKULAM",
              "tv_config": {
                "audio_language": "en",
                "orientation": "landscape",
                "layout_type": "default",
                "save_audio_external": false,
                "enable_counter_announcement": true,
                "enable_token_announcement": true,
                "enable_counter_prifix": true,
                "display_rows": 6,
                "display_columns": 1,
                "counter_text_color": "#000000",
                "token_text_color": "#000000",
                "scroll_text_color": "#000000",
                "token_font_size": 24,
                "counter_font_size": 24,
                "tokens_per_counter": 5,
                "no_of_counters": 2,
                "current_token_color": "#000000",
                "previous_token_color": "#888888",
                "blink_current_token": true,
                "blink_seconds": 5,
                "token_format": "T1",
                "ad_interval": 5,
                "show_ads": "on",
                "ad_placement": "right",
                "ad_files": [
                  "http://example.com/ad1.mp4",
                  "http://example.com/ad2.mp4"
                ]
              },
              "current_profile": null,
              "connected_devices": [
                {
                  "id": 31,
                  "serial_number": "AbCAL0B0002",
                  "device_type": "BROKER",
                  "name": "BLCN_002",
                  "status": "Active",
                  "is_active": true,
                  "config": {
                    "ssid": "ASIANET",
                    "password": "secret",
                    "host": "192.168.0.51",
                    "port": "1883",
                    "topic": "166-2026bCAL0B0002"
                  }
                },
                {
                  "id": 21,
                  "serial_number": "AbCAL0K0005",
                  "device_type": "KEYPAD",
                  "name": "KEYPAD GHE 5",
                  "status": "Active",
                  "is_active": true
                }
              ],
              "counters": [
                {
                  "counter_id": "NEPHROLOGY",
                  "default_code": "NE",
                  "keypad_index": "1",
                  "button_index": null,
                  "dispenser_button_index": "5",
                  "name": "NEPHROLOGY",
                  "code": "NE",
                  "row_span": 1,
                  "col_span": 1,
                  "is_enabled": true,
                  "counter_config_id": 4,
                  "max_token_number": 75,
                  "dispenser_sn": "2026bCAL0D0005"
                }
              ],
              "keypads": [
                {
                  "keypad_sn": "AbCAL0K0005",
                  "keypad_display_name": "KEYPAD GHE 5",
                  "keypad_index": "1",
                  "dispenser_sn": "AbCAL0D0005",
                  "button_strings": [
                    { "id": "1", "value": "NURSE CALLING" }
                  ],
                  "counters": [
                    {
                      "counter_id": "NEPHROLOGY",
                      "keypad_index": "1",
                      "button_index": null,
                      "dispenser_button_index": "5",
                      "name": "NEPHROLOGY",
                      "code": "NE",
                      "is_enabled": true
                    }
                  ]
                }
              ],
              "shift_details": null,
              "scroll_config": {
                "scroll_enabled": "on",
                "no_of_text_fields": 3,
                "scroll_text_lines": [
                  "Your health is your wealth",
                  "Eat Well, Live Well!"
                ],
                "scroll_text_color": "#000000"
              }
            }
        """.trimIndent()

        val response = gson.fromJson(json, TvConfigResponse::class.java)

        assertEquals("success", response.status)
        assertEquals(10, response.deviceId)
        assertEquals("GH ERNAKULAM", response.companyName)
        assertTrue(response.currentProfile == null || response.currentProfile!!.isJsonNull)
        assertTrue(response.shiftDetails == null || response.shiftDetails!!.isJsonNull)

        val tv = response.tvConfig!!
        assertEquals("default", tv.layoutType)
        assertEquals(5, tv.tokensPerCounter)
        assertEquals(5, tv.blinkSeconds)
        assertEquals("#000000", tv.scrollTextColor)
        assertEquals(2, tv.adFiles?.size)
        assertTrue(tv.showAds.equals("on", ignoreCase = true))

        val counter = response.counters!!.first()
        assertEquals("NEPHROLOGY", counter.counterId)
        assertNull(counter.buttonIndex)
        assertEquals(5, counter.dispenserButtonIndex)
        assertEquals("NE", counter.code)

        val keypad = response.keypads!!.first()
        assertEquals("AbCAL0K0005", keypad.keypadSn)
        assertEquals(1, keypad.buttonStrings?.size)
        assertEquals("NEPHROLOGY", keypad.counters?.first()?.counterId)

        assertEquals("on", response.scrollConfig?.scrollEnabled)
        assertEquals(3, response.scrollConfig?.noOfTextFields)
        assertEquals("#000000", response.scrollConfig?.scrollTextColor)

        val broker = response.connectedDevices!!.first()
        assertEquals("BROKER", broker.deviceType)
        assertNotNull(broker.config)

        val keypadDevice = response.connectedDevices[1]
        assertEquals("KEYPAD", keypadDevice.deviceType)
        assertNull(keypadDevice.config)
    }
}
