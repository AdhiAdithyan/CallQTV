package com.softland.callqtv.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CounterRouteLookupCacheTest {

    @Test
    fun routeCacheKey_normalizesSerialAndTrimsRoute() {
        assertEquals(
            "2026BCAL0K0007|2",
            MqttCounterRouteKeys.routeCacheKey(" 2026bcal0k0007 ", " 2 "),
        )
        assertEquals(
            "2026BCAL0K0007|",
            MqttCounterRouteKeys.routeCacheKey("2026BCAL0K0007", ""),
        )
    }

    @Test
    fun deviceScope_joinsMacAndCustomer() {
        assertEquals(
            "AA:BB:CC:DD:EE:FF|0042",
            MqttCounterRouteKeys.deviceScope("AA:BB:CC:DD:EE:FF", "0042"),
        )
    }

    @Test
    fun lookup_returnsMissWhenEmpty() {
        val cache = CounterRouteLookupCache<String>(ttlMs = 60_000L, nowMs = { 1_000L })
        assertTrue(cache.lookup("scope-a", "k1") is RouteCacheLookup.Miss)
    }

    @Test
    fun lookup_returnsStoredValueWithinTtl() {
        var now = 1_000L
        val cache = CounterRouteLookupCache<String>(ttlMs = 60_000L, nowMs = { now })
        val scope = MqttCounterRouteKeys.deviceScope("mac", "0001")
        cache.store(scope, "serial|1", "counter-1")

        val hit = cache.lookup(scope, "serial|1")
        assertTrue(hit is RouteCacheLookup.Hit)
        assertEquals("counter-1", (hit as RouteCacheLookup.Hit).value)

        now += 30_000L
        val hitLater = cache.lookup(scope, "serial|1")
        assertTrue(hitLater is RouteCacheLookup.Hit)
        assertEquals("counter-1", (hitLater as RouteCacheLookup.Hit).value)
    }

    @Test
    fun lookup_cachesNullNegativeResult() {
        val cache = CounterRouteLookupCache<ResolvedCounterIdentity>(
            ttlMs = 60_000L,
            nowMs = { 1_000L },
        )
        val scope = MqttCounterRouteKeys.deviceScope("mac", "0001")
        cache.store(scope, "unknown|", null)

        val hit = cache.lookup(scope, "unknown|")
        assertTrue(hit is RouteCacheLookup.Hit)
        assertNull((hit as RouteCacheLookup.Hit).value)
    }

    @Test
    fun lookup_missesAfterTtlExpires() {
        var now = 1_000L
        val cache = CounterRouteLookupCache<String>(ttlMs = 5_000L, nowMs = { now })
        val scope = MqttCounterRouteKeys.deviceScope("mac", "0001")
        cache.store(scope, "serial|", "value")

        now += 5_001L
        assertTrue(cache.lookup(scope, "serial|") is RouteCacheLookup.Miss)
    }

    @Test
    fun scopeChange_clearsPreviousEntries() {
        val cache = CounterRouteLookupCache<String>(ttlMs = 60_000L, nowMs = { 1_000L })
        val scopeA = MqttCounterRouteKeys.deviceScope("mac-a", "0001")
        val scopeB = MqttCounterRouteKeys.deviceScope("mac-b", "0001")
        cache.store(scopeA, "serial|1", "from-a")

        cache.lookup(scopeB, "serial|1")
        assertTrue(cache.lookup(scopeB, "serial|1") is RouteCacheLookup.Miss)
    }

    @Test
    fun invalidate_clearsAllEntries() {
        val cache = CounterRouteLookupCache<String>(ttlMs = 60_000L, nowMs = { 1_000L })
        val scope = MqttCounterRouteKeys.deviceScope("mac", "0001")
        cache.store(scope, "serial|1", "value")
        cache.invalidate()
        assertTrue(cache.lookup(scope, "serial|1") is RouteCacheLookup.Miss)
    }

    @Test
    fun differentKeys_sameScope_areIndependent() {
        val cache = CounterRouteLookupCache<String>(ttlMs = 60_000L, nowMs = { 1_000L })
        val scope = MqttCounterRouteKeys.deviceScope("mac", "0001")
        cache.store(scope, "serial|1", "route-1")
        cache.store(scope, "serial|2", "route-2")

        val hit1 = cache.lookup(scope, "serial|1") as RouteCacheLookup.Hit
        val hit2 = cache.lookup(scope, "serial|2") as RouteCacheLookup.Hit
        assertEquals("route-1", hit1.value)
        assertEquals("route-2", hit2.value)
    }
}
