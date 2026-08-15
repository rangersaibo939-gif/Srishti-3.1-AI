import { ToolDefinition, ToolExecutionResult } from './domain';

export const SAFE_TOOL_REGISTRY: Record<string, ToolDefinition> = {
  set_flashlight: {
    name: 'set_flashlight',
    description: 'Toggle or adjust the hardware camera torch/flashlight.',
    riskLevel: 'SAFE',
    requiredPermissions: ['android.permission.CAMERA'],
    idempotent: true,
    inputSchema: {
      type: 'object',
      properties: {
        enabled: { type: 'boolean', description: 'True to turn on, false to turn off.' },
        level: { type: 'number', description: 'Torch brightness level 1-100 (optional on supported Android devices).' }
      },
      required: ['enabled']
    },
    outputSchema: {
      type: 'object',
      properties: {
        currentState: { type: 'boolean', description: 'The verified torch state after setting.' }
      }
    }
  },

  set_media_volume: {
    name: 'set_media_volume',
    description: 'Set device media audio stream volume percentage (0-100).',
    riskLevel: 'SAFE',
    requiredPermissions: [],
    idempotent: true,
    inputSchema: {
      type: 'object',
      properties: {
        volumePercent: { type: 'number', description: 'Volume level from 0 to 100.' },
        showUi: { type: 'boolean', description: 'Whether to show the system volume slider overlay.' }
      },
      required: ['volumePercent']
    },
    outputSchema: {
      type: 'object',
      properties: {
        previousVolume: { type: 'number', description: 'Volume percent before adjustment.' },
        newVolume: { type: 'number', description: 'Verified volume percent after adjustment.' }
      }
    }
  },

  get_battery_info: {
    name: 'get_battery_info',
    description: 'Query battery percentage, charging state, health, and battery saver status.',
    riskLevel: 'SAFE',
    requiredPermissions: [],
    idempotent: true,
    inputSchema: {
      type: 'object',
      properties: {
        includeHealth: { type: 'boolean', description: 'Whether to include battery health metric.' }
      },
      required: []
    },
    outputSchema: {
      type: 'object',
      properties: {
        percentage: { type: 'number', description: 'Current battery level 0-100.' },
        isCharging: { type: 'boolean', description: 'Whether power is currently connected.' },
        powerSource: { type: 'string', description: 'AC, USB, Wireless, or Battery.' },
        isPowerSaveMode: { type: 'boolean', description: 'Whether OS Battery Saver is active.' }
      }
    }
  },

  open_installed_app: {
    name: 'open_installed_app',
    description: 'Launch an installed application by its explicit package name via Android PackageManager.',
    riskLevel: 'SAFE',
    requiredPermissions: [],
    idempotent: true,
    inputSchema: {
      type: 'object',
      properties: {
        packageName: { type: 'string', description: 'Canonical package name, e.g. com.google.android.calculator.' }
      },
      required: ['packageName']
    },
    outputSchema: {
      type: 'object',
      properties: {
        launched: { type: 'boolean', description: 'True if launch intent was successfully dispatched.' },
        packageName: { type: 'string', description: 'The package that was opened.' }
      }
    }
  }
};

/**
 * Simulated Native Android Tool Executor with Verification
 */
export class NativeToolExecutor {
  // Device hardware state simulation
  private static flashlightState: boolean = false;
  private static mediaVolume: number = 65;
  private static batteryLevel: number = 82;
  private static isCharging: boolean = false;

  public static getDeviceState() {
    return {
      flashlight: this.flashlightState,
      volume: this.mediaVolume,
      battery: this.batteryLevel,
      charging: this.isCharging
    };
  }

  public static async execute(
    toolName: string, 
    args: Record<string, unknown>,
    toolCallId: string
  ): Promise<ToolExecutionResult> {
    const tool = SAFE_TOOL_REGISTRY[toolName];
    if (!tool) {
      return {
        toolCallId,
        name: toolName,
        success: false,
        error: `REJECTED: Unknown tool '${toolName}'. Not in Phase 1 Safe Registry.`,
        verificationPassed: false
      };
    }

    try {
      switch (toolName) {
        case 'set_flashlight': {
          const target = Boolean(args.enabled);
          // Android CameraManager.setTorchMode("0", target)
          this.flashlightState = target;
          // Verification: Read torch callback state
          const verified = this.flashlightState === target;
          return {
            toolCallId,
            name: toolName,
            success: true,
            data: { currentState: this.flashlightState, torchMode: this.flashlightState ? 'ON' : 'OFF' },
            verificationPassed: verified,
            verificationDetails: `CameraManager.TorchCallback verified torch=${this.flashlightState ? 'ACTIVE' : 'INACTIVE'}`
          };
        }

        case 'set_media_volume': {
          const rawVol = Number(args.volumePercent);
          if (isNaN(rawVol) || rawVol < 0 || rawVol > 100) {
            return {
              toolCallId,
              name: toolName,
              success: false,
              error: `Invalid volumePercent ${args.volumePercent}. Must be between 0 and 100.`,
              verificationPassed: false
            };
          }
          const prev = this.mediaVolume;
          this.mediaVolume = Math.round(rawVol);
          // Verification: AudioManager.getStreamVolume(STREAM_MUSIC)
          const verified = this.mediaVolume === Math.round(rawVol);
          return {
            toolCallId,
            name: toolName,
            success: true,
            data: { previousVolume: prev, newVolume: this.mediaVolume },
            verificationPassed: verified,
            verificationDetails: `AudioManager.getStreamVolume(STREAM_MUSIC) read ${this.mediaVolume}%`
          };
        }

        case 'get_battery_info': {
          // Android BatteryManager intent reading
          return {
            toolCallId,
            name: toolName,
            success: true,
            data: {
              percentage: this.batteryLevel,
              isCharging: this.isCharging,
              powerSource: this.isCharging ? 'USB_PD' : 'BATTERY',
              isPowerSaveMode: false
            },
            verificationPassed: true,
            verificationDetails: `BatteryManager.EXTRA_LEVEL returned ${this.batteryLevel}%`
          };
        }

        case 'open_installed_app': {
          const pkg = String(args.packageName || '');
          if (!pkg || !pkg.includes('.')) {
            return {
              toolCallId,
              name: toolName,
              success: false,
              error: `Invalid Android package name '${pkg}'. Format must be reverse-domain like 'com.example.app'.`,
              verificationPassed: false
            };
          }

          // Whitelist simulation of valid Android system packages
          const validPackages = [
            'com.google.android.calculator',
            'com.google.android.deskclock',
            'com.google.android.calendar',
            'com.android.settings',
            'com.google.android.apps.photos'
          ];

          const isInstalled = validPackages.includes(pkg) || pkg.startsWith('com.');
          if (!isInstalled) {
            return {
              toolCallId,
              name: toolName,
              success: false,
              error: `PackageManager.NameNotFoundException: Package '${pkg}' is not installed on this device.`,
              verificationPassed: false
            };
          }

          return {
            toolCallId,
            name: toolName,
            success: true,
            data: { launched: true, packageName: pkg },
            verificationPassed: true,
            verificationDetails: `PackageManager.getLaunchIntentForPackage('${pkg}') dispatched Intent.FLAG_ACTIVITY_NEW_TASK`
          };
        }

        default:
          return {
            toolCallId,
            name: toolName,
            success: false,
            error: `Unhandled tool '${toolName}'.`,
            verificationPassed: false
          };
      }
    } catch (err: unknown) {
      return {
        toolCallId,
        name: toolName,
        success: false,
        error: `Native Execution Exception: ${err instanceof Error ? err.message : String(err)}`,
        verificationPassed: false
      };
    }
  }
}
