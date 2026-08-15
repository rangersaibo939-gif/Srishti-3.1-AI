package com.opendroid.app

import com.opendroid.app.core.domain.RiskLevel
import com.opendroid.app.core.domain.ToolCallRequest
import com.opendroid.app.core.risk.RiskEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RiskEngineTest {

    @Test
    fun testSafeFlashlightTool() {
        val request = ToolCallRequest(
            name = "set_flashlight",
            arguments = mapOf("enabled" to true)
        )
        val result = RiskEngine.evaluate(request)
        assertEquals(RiskLevel.SAFE, result.tier)
        assertTrue(result.allowedToExecute)
        assertFalse(result.requiresUserConfirmation)
        assertFalse(result.requiresBiometricAuth)
    }

    @Test
    fun testVolumeUnder85IsSafe() {
        val request = ToolCallRequest(
            name = "set_media_volume",
            arguments = mapOf("volumePercent" to 60)
        )
        val result = RiskEngine.evaluate(request)
        assertEquals(RiskLevel.SAFE, result.tier)
        assertTrue(result.allowedToExecute)
        assertFalse(result.requiresUserConfirmation)
    }

    @Test
    fun testVolumeOver85RequiresConfirmation() {
        val request = ToolCallRequest(
            name = "set_media_volume",
            arguments = mapOf("volumePercent" to 95)
        )
        val result = RiskEngine.evaluate(request)
        assertEquals(RiskLevel.CONFIRM, result.tier)
        assertFalse(result.allowedToExecute)
        assertTrue(result.requiresUserConfirmation)
    }

    @Test
    fun testLaunchBankingAppRequiresBiometrics() {
        val request = ToolCallRequest(
            name = "open_installed_app",
            arguments = mapOf("packageName" to "com.bank.app")
        )
        val result = RiskEngine.evaluate(request)
        assertEquals(RiskLevel.HIGH_RISK, result.tier)
        assertFalse(result.allowedToExecute)
        assertTrue(result.requiresUserConfirmation)
        assertTrue(result.requiresBiometricAuth)
    }

    @Test
    fun testLaunchStandardAppIsSafe() {
        val request = ToolCallRequest(
            name = "open_installed_app",
            arguments = mapOf("packageName" to "com.spotify.music")
        )
        val result = RiskEngine.evaluate(request)
        assertEquals(RiskLevel.SAFE, result.tier)
        assertTrue(result.allowedToExecute)
        assertFalse(result.requiresUserConfirmation)
    }

    @Test
    fun testArbitraryShellIsBlocked() {
        val request = ToolCallRequest(
            name = "execute_shell_command",
            arguments = mapOf("cmd" to "rm -rf /")
        )
        val result = RiskEngine.evaluate(request)
        assertEquals(RiskLevel.BLOCKED, result.tier)
        assertFalse(result.allowedToExecute)
    }
}
