import { 
  BoundedMessage, 
  PersistedTask, 
  StructuredModelDecision, 
  TaskStatus, 
  ToolCallRequest, 
  ToolExecutionResult 
} from './domain';
import { RiskEngine } from './riskEngine';
import { NativeToolExecutor, SAFE_TOOL_REGISTRY } from './tools';
import { RoomTaskRepository } from './roomStore';
import { InferenceEngine } from './inference';

export interface AgentExecutionCallback {
  onLog?: (log: string) => void;
  onStateChange?: (status: TaskStatus) => void;
  onConfirmationRequired?: (toolCall: ToolCallRequest, reason: string, onConfirm: () => void, onReject: () => void) => void;
}

/**
 * In-Process AgentCore Coordinator
 * Frozen Phase 1 Architecture
 */
export class AgentCore {
  private static emergencyStopActive: boolean = false;
  private static cancellationToken = { isCancelled: false };

  public static isEmergencyStopActive(): boolean {
    return this.emergencyStopActive;
  }

  public static triggerEmergencyStop(): void {
    this.emergencyStopActive = true;
    this.cancellationToken.isCancelled = true;
    console.warn('EMERGENCY STOP TRIGGERED: All inference, active IO, and pending tools cancelled.');
  }

  public static resetEmergencyStop(): void {
    this.emergencyStopActive = false;
    this.cancellationToken.isCancelled = false;
  }

  /**
   * Generates deterministic UUIDv5-like key for idempotency
   */
  private static generateIdempotencyKey(taskId: string, stepIndex: number, toolName: string, args: Record<string, unknown>): string {
    const raw = `${taskId}:${stepIndex}:${toolName}:${JSON.stringify(args)}`;
    // Simple deterministic hash
    let hash = 0;
    for (let i = 0; i < raw.length; i++) {
      hash = ((hash << 5) - hash) + raw.charCodeAt(i);
      hash |= 0;
    }
    return `key_${taskId.slice(0, 8)}_${stepIndex}_${Math.abs(hash).toString(16)}`;
  }

  /**
   * Main Autonomous Pipeline Loop
   */
  public static async executeTask(
    userPrompt: string,
    callbacks?: AgentExecutionCallback
  ): Promise<PersistedTask> {
    const log = (msg: string) => {
      callbacks?.onLog?.(`[${new Date().toLocaleTimeString()}] ${msg}`);
    };

    if (this.emergencyStopActive) {
      throw new Error('AgentCore halted: Emergency Stop is currently active.');
    }

    const taskId = `task_${Date.now()}_${Math.random().toString(36).substr(2, 6)}`;
    const task: PersistedTask = {
      id: taskId,
      userPrompt,
      status: 'CREATED',
      provider: 'LOCAL_LLAMA',
      model: 'qwen2.5-1.5b-instruct-q4_k_m',
      steps: [],
      createdAt: Date.now(),
      updatedAt: Date.now()
    };

    // 1. Initial Room DB Write
    await RoomTaskRepository.insertTask(task);
    log(`Task [${taskId}] initialized in Room DB. Status: CREATED.`);
    callbacks?.onStateChange?.('CREATED');

    // 2. Analyzing & Context Bounding
    await RoomTaskRepository.updateTaskStatus(taskId, 'ANALYZING');
    log('AgentCore analyzing bounded context...');
    callbacks?.onStateChange?.('ANALYZING');

    const messages: BoundedMessage[] = [
      {
        role: 'system',
        content: 'You are OpenDroid, a private local assistant on Android. Use registered safe tools.'
      },
      {
        role: 'user',
        content: userPrompt
      }
    ];

    // 3. Inference Execution
    let decision: StructuredModelDecision;
    try {
      decision = await InferenceEngine.infer(messages, {
        grammarConstraint: 'GBNF_TOOL_CALL',
        cancellationToken: this.cancellationToken
      });
      log(`Structured decision received: ${decision.type}`);
    } catch (err: unknown) {
      const errorMsg = err instanceof Error ? err.message : String(err);
      await RoomTaskRepository.updateTaskStatus(taskId, 'FAILED', errorMsg);
      log(`Inference failed: ${errorMsg}`);
      callbacks?.onStateChange?.('FAILED');
      task.status = 'FAILED';
      task.errorMessage = errorMsg;
      return task;
    }

    // Direct Response handling
    if (decision.type === 'DIRECT_RESPONSE' || !decision.toolCall) {
      const reply = decision.response || 'Task completed without tool execution.';
      await RoomTaskRepository.updateTaskStatus(taskId, 'COMPLETED', undefined, reply);
      log(`Task finished with direct response: "${reply}"`);
      callbacks?.onStateChange?.('COMPLETED');
      task.status = 'COMPLETED';
      task.finalResponse = reply;
      return task;
    }

    // Tool Call execution flow
    const toolCall = decision.toolCall;
    log(`Model requested tool: ${toolCall.name} with args: ${JSON.stringify(toolCall.arguments)}`);

    // 4. Schema & Registry Validation
    const toolDef = SAFE_TOOL_REGISTRY[toolCall.name];
    if (!toolDef) {
      const errorMsg = `Unknown tool '${toolCall.name}' requested. Rejection enforced.`;
      await RoomTaskRepository.updateTaskStatus(taskId, 'FAILED', errorMsg);
      log(`VALIDATION FAILURE: ${errorMsg}`);
      callbacks?.onStateChange?.('FAILED');
      task.status = 'FAILED';
      task.errorMessage = errorMsg;
      return task;
    }

    // 5. Pure Risk Engine Evaluation
    const riskEval = RiskEngine.evaluate(toolCall);
    log(`Risk Engine evaluation: [${riskEval.tier}] - ${riskEval.reason}`);

    if (riskEval.tier === 'BLOCKED') {
      const errorMsg = `BLOCKED by Risk Engine: ${riskEval.reason}`;
      await RoomTaskRepository.updateTaskStatus(taskId, 'FAILED', errorMsg);
      log(errorMsg);
      callbacks?.onStateChange?.('FAILED');
      task.status = 'FAILED';
      task.errorMessage = errorMsg;
      return task;
    }

    // 6. Planned State & Idempotency Key
    const idempotencyKey = this.generateIdempotencyKey(taskId, 0, toolCall.name, toolCall.arguments);
    const step: import('./domain').PersistedTaskStep = {
      stepIndex: 0,
      idempotencyKey,
      toolName: toolCall.name,
      argumentsJson: JSON.stringify(toolCall.arguments),
      riskTier: riskEval.tier,
      status: 'PLANNED',
      verified: false,
      timestamp: Date.now()
    };

    await RoomTaskRepository.appendStep(taskId, step);
    await RoomTaskRepository.updateTaskStatus(taskId, 'PLANNED');
    log(`Step planned with idempotency key ${idempotencyKey}.`);
    callbacks?.onStateChange?.('PLANNED');

    // 7. Confirmation Check
    if (riskEval.requiresUserConfirmation || riskEval.tier === 'CONFIRM' || riskEval.tier === 'HIGH_RISK') {
      await RoomTaskRepository.updateTaskStatus(taskId, 'WAITING_CONFIRMATION');
      log(`Action requires user confirmation: ${riskEval.reason}`);
      callbacks?.onStateChange?.('WAITING_CONFIRMATION');

      // Handled asynchronously or synchronously
      const confirmed = await new Promise<boolean>((resolve) => {
        if (callbacks?.onConfirmationRequired) {
          callbacks.onConfirmationRequired(
            toolCall,
            riskEval.reason,
            () => resolve(true),
            () => resolve(false)
          );
        } else {
          // Default auto-approve in test harness if no UI callback
          resolve(true);
        }
      });

      if (!confirmed) {
        await RoomTaskRepository.updateTaskStatus(taskId, 'FAILED', 'User rejected confirmation dialog.');
        log('User rejected confirmation. Task aborted.');
        callbacks?.onStateChange?.('FAILED');
        task.status = 'FAILED';
        task.errorMessage = 'Action rejected by user.';
        return task;
      }
      log('User confirmation received.');
    }

    // 8. Execution State
    if (this.cancellationToken.isCancelled) {
      await RoomTaskRepository.updateTaskStatus(taskId, 'FAILED', 'Emergency Stop halted execution.');
      callbacks?.onStateChange?.('FAILED');
      task.status = 'FAILED';
      task.errorMessage = 'Emergency Stop active.';
      return task;
    }

    await RoomTaskRepository.updateTaskStatus(taskId, 'EXECUTING');
    await RoomTaskRepository.updateStepStatus(taskId, idempotencyKey, 'EXECUTING');
    log(`Executing tool [${toolCall.name}] via Android native API...`);
    callbacks?.onStateChange?.('EXECUTING');

    const result: ToolExecutionResult = await NativeToolExecutor.execute(
      toolCall.name,
      toolCall.arguments,
      toolCall.id
    );

    // 9. Verification State
    await RoomTaskRepository.updateTaskStatus(taskId, 'VERIFYING');
    log(`Verifying operation... Verification: ${result.verificationPassed ? 'PASSED' : 'FAILED'}. ${result.verificationDetails || ''}`);
    callbacks?.onStateChange?.('VERIFYING');

    await RoomTaskRepository.updateStepStatus(
      taskId, 
      idempotencyKey, 
      result.success ? 'COMPLETED' : 'FAILED',
      JSON.stringify(result),
      result.verificationPassed
    );

    if (result.success && result.verificationPassed) {
      const finalReply = `Operation ${toolCall.name} succeeded. ${result.verificationDetails || ''}`;
      await RoomTaskRepository.updateTaskStatus(taskId, 'COMPLETED', undefined, finalReply);
      log(`Task [${taskId}] successfully COMPLETED.`);
      callbacks?.onStateChange?.('COMPLETED');
      task.status = 'COMPLETED';
      task.finalResponse = finalReply;
    } else {
      const failReason = result.error || 'Verification failed post-execution.';
      await RoomTaskRepository.updateTaskStatus(taskId, 'FAILED', failReason);
      log(`Task FAILED: ${failReason}`);
      callbacks?.onStateChange?.('FAILED');
      task.status = 'FAILED';
      task.errorMessage = failReason;
    }

    return task;
  }
}
