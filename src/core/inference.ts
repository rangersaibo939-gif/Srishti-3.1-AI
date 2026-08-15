import { BoundedMessage, StructuredModelDecision } from './domain';
import { SAFE_TOOL_REGISTRY } from './tools';

export interface InferenceOptions {
  maxTokens?: number;
  temperature?: number;
  grammarConstraint?: 'GBNF_TOOL_CALL' | 'NONE';
  cancellationToken?: { isCancelled: boolean };
}

/**
 * Isolated Out-of-Process Inference Simulator
 * Emulates Android :inference service over AIDL with llama.cpp
 */
export class InferenceEngine {
  private static isServiceAlive: boolean = true;
  private static simulatedCrashCount: number = 0;

  public static setServiceStatus(alive: boolean) {
    this.isServiceAlive = alive;
  }

  public static getServiceStatus() {
    return {
      alive: this.isServiceAlive,
      crashesHandled: this.simulatedCrashCount,
      activeModel: 'Qwen 1.5B Q4_K_M (1.1GB RAM)'
    };
  }

  public static triggerSimulatedCrash() {
    this.isServiceAlive = false;
    this.simulatedCrashCount++;
    // Watchdog restarts service after 1.5s
    setTimeout(() => {
      this.isServiceAlive = true;
    }, 1500);
  }

  /**
   * Deterministic GBNF-constrained Parser and Generator for Local Models
   */
  public static async infer(
    messages: BoundedMessage[],
    options: InferenceOptions = {}
  ): Promise<StructuredModelDecision> {
    if (options.cancellationToken?.isCancelled) {
      throw new Error('Inference cancelled by Emergency Stop.');
    }

    if (!this.isServiceAlive) {
      throw new Error('InferenceServiceUnavailable: Isolated process :inference is dead or restarting.');
    }

    const lastMessage = messages[messages.length - 1]?.content.toLowerCase() || '';

    // Fast deterministic matching for Phase 1 testing
    await new Promise(r => setTimeout(r, 450));

    if (options.cancellationToken?.isCancelled) {
      throw new Error('Inference cancelled by Emergency Stop.');
    }

    // 1. Flashlight intent
    if (lastMessage.includes('torch') || lastMessage.includes('flashlight')) {
      const turnOn = !lastMessage.includes('off') && !lastMessage.includes('disable');
      return {
        type: 'TOOL_CALL',
        thought: 'User requested flashlight state change. Generating set_flashlight schema decision.',
        toolCall: {
          id: `call_${Date.now()}_${Math.random().toString(36).substr(2, 5)}`,
          name: 'set_flashlight',
          arguments: { enabled: turnOn }
        }
      };
    }

    // 2. Volume intent
    if (lastMessage.includes('volume') || lastMessage.includes('sound') || lastMessage.includes('audio')) {
      const match = lastMessage.match(/\d+/);
      const vol = match ? parseInt(match[0], 10) : 50;
      return {
        type: 'TOOL_CALL',
        thought: `User requested setting volume to ${vol}%.`,
        toolCall: {
          id: `call_${Date.now()}_${Math.random().toString(36).substr(2, 5)}`,
          name: 'set_media_volume',
          arguments: { volumePercent: vol, showUi: true }
        }
      };
    }

    // 3. Battery intent
    if (lastMessage.includes('battery') || lastMessage.includes('power') || lastMessage.includes('charge')) {
      return {
        type: 'TOOL_CALL',
        thought: 'Querying system battery metrics via Android BatteryManager.',
        toolCall: {
          id: `call_${Date.now()}_${Math.random().toString(36).substr(2, 5)}`,
          name: 'get_battery_info',
          arguments: { includeHealth: true }
        }
      };
    }

    // 4. Open app intent
    if (lastMessage.includes('open') || lastMessage.includes('launch') || lastMessage.includes('calculator') || lastMessage.includes('settings')) {
      let pkg = 'com.google.android.calculator';
      if (lastMessage.includes('settings')) pkg = 'com.android.settings';
      if (lastMessage.includes('photos')) pkg = 'com.google.android.apps.photos';
      if (lastMessage.includes('clock')) pkg = 'com.google.android.deskclock';

      return {
        type: 'TOOL_CALL',
        thought: `Resolving package name for app launch intent: ${pkg}`,
        toolCall: {
          id: `call_${Date.now()}_${Math.random().toString(36).substr(2, 5)}`,
          name: 'open_installed_app',
          arguments: { packageName: pkg }
        }
      };
    }

    // 5. Default conversational text
    return {
      type: 'DIRECT_RESPONSE',
      thought: 'No native system tool required.',
      response: `I am OpenDroid running locally with Qwen 1.5B (Q4_K_M). I can operate your flashlight, adjust volume, check battery status, and launch apps safely.`
    };
  }
}
