package com.opendroid.app.core.task

import com.opendroid.app.core.logging.RedactedLogger
import kotlinx.coroutines.Job
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Real Global Emergency Stop Manager
 * Performs atomic cancellation of active coroutine jobs, pending tools, and IPC inference.
 */
object EmergencyStopManager {

    private const val TAG = "EmergencyStop"
    private val isHalted = AtomicBoolean(false)
    private var activeAgentJob: Job? = null
    private var cancelIpcCallback: (() -> Unit)? = null

    fun isEmergencyStopActive(): Boolean = isHalted.get()

    fun registerActiveJob(job: Job?, onCancelIpc: (() -> Unit)? = null) {
        this.activeAgentJob = job
        this.cancelIpcCallback = onCancelIpc
    }

    fun triggerEmergencyStop() {
        isHalted.set(true)
        RedactedLogger.w(TAG, "GLOBAL EMERGENCY STOP TRIGGERED: Cancelling all active jobs & IPC...")

        // 1. Cancel active Coroutine pipeline
        activeAgentJob?.cancel()
        activeAgentJob = null

        // 2. Trigger native IPC cancellation
        try {
            cancelIpcCallback?.invoke()
        } catch (e: Exception) {
            RedactedLogger.e(TAG, "Error invoking IPC cancellation callback", e)
        }
        cancelIpcCallback = null
    }

    fun reset() {
        isHalted.set(false)
        activeAgentJob = null
        cancelIpcCallback = null
        RedactedLogger.i(TAG, "Emergency stop state reset to NORMAL.")
    }
}
