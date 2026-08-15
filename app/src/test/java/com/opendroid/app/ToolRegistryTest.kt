package com.opendroid.app

import com.opendroid.app.core.tools.ToolRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolRegistryTest {

    @Test
    fun testPhase1ToolsAreRegistered() {
        val tools = ToolRegistry.getAllTools()
        assertEquals(4, tools.size)

        assertNotNull(ToolRegistry.getTool("set_flashlight"))
        assertNotNull(ToolRegistry.getTool("set_media_volume"))
        assertNotNull(ToolRegistry.getTool("get_battery_info"))
        assertNotNull(ToolRegistry.getTool("open_installed_app"))
        assertNull(ToolRegistry.getTool("unregistered_random_tool"))
    }

    @Test
    fun testToolDefinitionsAreValid() {
        val defs = ToolRegistry.getToolDefinitions()
        assertEquals(4, defs.size)

        for (def in defs) {
            assertTrue(def.name.isNotBlank())
            assertTrue(def.description.isNotBlank())
            assertTrue(def.parameters.isNotBlank())
        }
    }
}
