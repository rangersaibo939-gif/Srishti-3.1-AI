export interface AuditSection {
  id: string;
  title: string;
  badge?: string;
  category: 'core' | 'analysis' | 'decisions' | 'rules';
}

export interface DecisionItem {
  component: string;
  buildNow: boolean;
  designNow: boolean;
  later: boolean;
  reject: boolean;
  reason: string;
}

export interface RiskItem {
  id: string;
  risk: string;
  severity: 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW';
  probability: 'HIGH' | 'MEDIUM' | 'LOW';
  mitigation: string;
  phase: string;
}

export interface RewriteKiller {
  id: number;
  killer: string;
  danger: string;
  architecturalFix: string;
}

export interface DisagreementResolution {
  topic: string;
  claudeView: string;
  deepSeekView: string;
  winner: 'Claude' | 'DeepSeek' | 'Synthesized / Corrected';
  rationale: string;
  frozenDecision: string;
}

export interface ImplementationStep {
  step: number;
  title: string;
  description: string;
  componentsTouched: string[];
  verificationCheck: string;
  deliverables: string[];
}

export interface AcceptanceTest {
  id: number;
  code: string;
  name: string;
  target: string;
  description: string;
  expectedOutcome: string;
  simSteps: string[];
  failureSimSteps?: string[];
}

export interface SafeToolSpec {
  id: string;
  name: string;
  description: string;
  apiUsed: string;
  riskTier: 'SAFE' | 'CONFIRM' | 'HIGH_RISK' | 'BLOCKED';
  permissions: string[];
  inputSchema: Record<string, unknown>;
  outputSchema: Record<string, unknown>;
  verificationLogic: string;
  idempotent: boolean;
}

export interface MasterContractSection {
  sectionNumber: number;
  title: string;
  category: 'Core Directives' | 'Inference & Isolation' | 'Tooling & Safety' | 'State & Persistence' | 'Testing & Delivery';
  summary: string;
  mandatoryRules: string[];
}
