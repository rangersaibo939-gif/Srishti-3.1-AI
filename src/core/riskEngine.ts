import { RiskLevel, ToolCallRequest } from './domain';
import { SAFE_TOOL_REGISTRY } from './tools';

export interface RiskEvaluationResult {
  tier: RiskLevel;
  allowedToExecute: boolean;
  requiresUserConfirmation: boolean;
  requiresBiometricAuth: boolean;
  reason: string;
}

/**
 * Pure Deterministic 4-Tier Risk Engine
 * The LLM CANNOT override this risk evaluation.
 */
export class RiskEngine {
  // Explicit high-risk operations
  private static readonly HIGH_RISK_PACKAGES = [
    'com.android.settings',
    'com.google.android.apps.walletnfcrel',
    'com.bank.app'
  ];

  public static evaluate(toolCall: ToolCallRequest): RiskEvaluationResult {
    const { name, arguments: args } = toolCall;
    const toolDef = SAFE_TOOL_REGISTRY[name];

    // 1. BLOCKED check: Unknown or forbidden tools
    if (!toolDef) {
      return {
        tier: 'BLOCKED',
        allowedToExecute: false,
        requiresUserConfirmation: false,
        requiresBiometricAuth: false,
        reason: `Operation BLOCKED: Tool '${name}' is not authorized in Phase 1 Safe Registry.`
      };
    }

    // Explicit forbidden behaviors
    if (name.includes('shell') || name.includes('accessibility') || name.includes('whatsapp') || name.includes('purchase')) {
      return {
        tier: 'BLOCKED',
        allowedToExecute: false,
        requiresUserConfirmation: false,
        requiresBiometricAuth: false,
        reason: `Operation BLOCKED: System policy strictly forbids arbitrary automation or financial primitives.`
      };
    }

    // 2. Specific tool checks
    if (name === 'open_installed_app') {
      const pkg = String(args.packageName || '');
      if (this.HIGH_RISK_PACKAGES.includes(pkg)) {
        return {
          tier: 'HIGH_RISK',
          allowedToExecute: false,
          requiresUserConfirmation: true,
          requiresBiometricAuth: true,
          reason: `Opening privileged system settings or financial app '${pkg}' requires Biometric Authentication.`
        };
      }
      return {
        tier: 'SAFE',
        allowedToExecute: true,
        requiresUserConfirmation: false,
        requiresBiometricAuth: false,
        reason: `Opening app '${pkg}' is classified as SAFE under standard Android Intent dispatch.`
      };
    }

    if (name === 'set_media_volume') {
      const vol = Number(args.volumePercent);
      if (vol > 85) {
        return {
          tier: 'CONFIRM',
          allowedToExecute: false,
          requiresUserConfirmation: true,
          requiresBiometricAuth: false,
          reason: `Setting volume above 85% (${vol}%) may cause hearing discomfort and requires user confirmation.`
        };
      }
      return {
        tier: 'SAFE',
        allowedToExecute: true,
        requiresUserConfirmation: false,
        requiresBiometricAuth: false,
        reason: `Setting media volume to ${vol}% is SAFE.`
      };
    }

    if (name === 'set_flashlight') {
      return {
        tier: 'SAFE',
        allowedToExecute: true,
        requiresUserConfirmation: false,
        requiresBiometricAuth: false,
        reason: `Controlling camera torch is SAFE.`
      };
    }

    if (name === 'get_battery_info') {
      return {
        tier: 'SAFE',
        allowedToExecute: true,
        requiresUserConfirmation: false,
        requiresBiometricAuth: false,
        reason: `Querying battery status is read-only and SAFE.`
      };
    }

    return {
      tier: 'CONFIRM',
      allowedToExecute: false,
      requiresUserConfirmation: true,
      requiresBiometricAuth: false,
      reason: `Default fallback: Confirmation required for tool ${name}.`
    };
  }
}
