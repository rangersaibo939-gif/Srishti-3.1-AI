/**
 * OpenDroid Phase 1 Core Domain Models & Interfaces
 * Frozen Architecture Specification
 */

export type TaskStatus = 
  | 'CREATED'
  | 'ANALYZING'
  | 'PLANNED'
  | 'WAITING_CONFIRMATION'
  | 'EXECUTING'
  | 'VERIFYING'
  | 'COMPLETED'
  | 'FAILED';

export type RiskLevel = 'SAFE' | 'CONFIRM' | 'HIGH_RISK' | 'BLOCKED';

export type ProviderType = 'LOCAL_LLAMA' | 'CLOUD_GEMINI';

export interface BoundedMessage {
  role: 'system' | 'user' | 'assistant' | 'tool';
  content: string;
  toolCallId?: string;
  name?: string;
}

export interface ToolCallRequest {
  id: string; // UUID
  name: string;
  arguments: Record<string, unknown>;
}

export interface StructuredModelDecision {
  type: 'TOOL_CALL' | 'DIRECT_RESPONSE' | 'ERROR';
  thought?: string;
  toolCall?: ToolCallRequest;
  response?: string;
  rawText?: string;
}

export interface ToolExecutionResult {
  toolCallId: string;
  name: string;
  success: boolean;
  data?: Record<string, unknown>;
  error?: string;
  verificationPassed: boolean;
  verificationDetails?: string;
}

export interface PersistedTaskStep {
  stepIndex: number;
  idempotencyKey: string; // UUIDv5
  toolName: string;
  argumentsJson: string;
  riskTier: RiskLevel;
  status: TaskStatus;
  userConfirmed?: boolean;
  resultJson?: string;
  verified: boolean;
  timestamp: number;
}

export interface PersistedTask {
  id: string; // UUID
  userPrompt: string;
  status: TaskStatus;
  provider: ProviderType;
  model: string;
  steps: PersistedTaskStep[];
  finalResponse?: string;
  errorMessage?: string;
  createdAt: number;
  updatedAt: number;
}

export interface ToolDefinition {
  name: string;
  description: string;
  inputSchema: {
    type: 'object';
    properties: Record<string, { type: string; description: string; enum?: string[] }>;
    required: string[];
  };
  outputSchema: {
    type: 'object';
    properties: Record<string, { type: string; description: string }>;
  };
  riskLevel: RiskLevel;
  requiredPermissions: string[];
  idempotent: boolean;
}

export interface AgentDiagnostics {
  agentCoreAlive: boolean;
  roomDatabaseAlive: boolean;
  isolatedInferenceServiceAlive: boolean;
  localModelLoaded: boolean;
  cloudProviderReachable: boolean;
  toolRegistryCount: number;
  emergencyStopArmStatus: 'READY' | 'TRIGGERED';
  activeTasksCount: number;
  lastError?: string;
}
