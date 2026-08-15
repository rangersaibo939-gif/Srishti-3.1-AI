package com.opendroid.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.opendroid.app.OpenDroidApplication
import com.opendroid.app.core.agent.AgentCore
import com.opendroid.app.core.agent.AgentExecutionCallback
import com.opendroid.app.core.domain.TaskStatus
import com.opendroid.app.core.domain.ToolCallRequest
import com.opendroid.app.core.inference.InferenceClient
import com.opendroid.app.core.task.EmergencyStopManager
import com.opendroid.app.data.database.TaskEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

data class PendingConfirmation(
    val toolCall: ToolCallRequest,
    val reason: String,
    val onDecision: (Boolean) -> Unit
)

class AgentViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as OpenDroidApplication
    private val repository = app.taskRepository
    private val inferenceClient = InferenceClient(application)
    private val agentCore = AgentCore(application, repository, inferenceClient)

    val allTasks = repository.allTasksFlow

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private val _currentStatus = MutableStateFlow(TaskStatus.CREATED)
    val currentStatus: StateFlow<TaskStatus> = _currentStatus.asStateFlow()

    private val _pendingConfirmation = MutableStateFlow<PendingConfirmation?>(null)
    val pendingConfirmation: StateFlow<PendingConfirmation?> = _pendingConfirmation.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    init {
        inferenceClient.bind()
    }

    fun executePrompt(prompt: String) {
        if (_isRunning.value) return
        _isRunning.value = true
        _logs.value = listOf("Starting execution for: \"$prompt\"")

        val job = viewModelScope.launch {
            try {
                agentCore.executeTask(
                    userPrompt = prompt,
                    callbacks = object : AgentExecutionCallback {
                        override fun onLog(message: String) {
                            _logs.value = _logs.value + message
                        }

                        override fun onStateChange(status: TaskStatus) {
                            _currentStatus.value = status
                        }

                        override suspend fun onConfirmationRequired(
                            toolCall: ToolCallRequest,
                            reason: String
                        ): Boolean = suspendCoroutine { continuation ->
                            _pendingConfirmation.value = PendingConfirmation(
                                toolCall = toolCall,
                                reason = reason,
                                onDecision = { approved ->
                                    _pendingConfirmation.value = null
                                    continuation.resume(approved)
                                }
                            )
                        }
                    }
                )
            } finally {
                _isRunning.value = false
            }
        }

        EmergencyStopManager.registerActiveJob(job) {
            inferenceClient.cancelInflightInference()
        }
    }

    fun triggerEmergencyStop() {
        EmergencyStopManager.triggerEmergencyStop()
        _logs.value = _logs.value + "[EMERGENCY_STOP] Pipeline cancelled by user."
        _currentStatus.value = TaskStatus.FAILED
        _isRunning.value = false
        _pendingConfirmation.value = null
        EmergencyStopManager.reset()
    }

    override fun onCleared() {
        super.onCleared()
        inferenceClient.unbind()
    }
}
