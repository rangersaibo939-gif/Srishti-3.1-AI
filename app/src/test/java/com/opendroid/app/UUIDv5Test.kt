package com.opendroid.app

import com.opendroid.app.core.security.UUIDv5
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.util.UUID

class UUIDv5Test {

    @Test
    fun testUUIDv5Determinism() {
        val ns = UUIDv5.OPENDROID_NAMESPACE
        val name = "test-operation-name"

        val uuid1 = UUIDv5.generate(ns, name)
        val uuid2 = UUIDv5.generate(ns, name)

        assertEquals("UUIDv5 must be strictly deterministic for identical inputs", uuid1, uuid2)
        assertEquals("UUID version must be 5", 5, uuid1.version())
        assertEquals("UUID variant must be 2 (RFC 4122 / Leach-Salz)", 2, uuid1.variant())
    }

    @Test
    fun testUUIDv5DifferentiatesDifferentSteps() {
        val key1 = UUIDv5.forStep("task-123", 0, "set_flashlight", "{\"enabled\":true}")
        val key2 = UUIDv5.forStep("task-123", 1, "set_flashlight", "{\"enabled\":true}")
        val key3 = UUIDv5.forStep("task-123", 0, "set_flashlight", "{\"enabled\":false}")

        assertNotEquals(key1, key2)
        assertNotEquals(key1, key3)
    }
}
