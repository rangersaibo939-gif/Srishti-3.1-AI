import React, { useState } from 'react';
import { 
  ShieldAlert, 
  CheckCircle2, 
  XCircle, 
  AlertTriangle, 
  Cpu, 
  Layers, 
  Database, 
  Smartphone, 
  Terminal, 
  FileText, 
  Sliders, 
  Zap, 
  Lock, 
  GitPullRequest, 
  ArrowRight,
  Play,
  RotateCcw,
  Sparkles,
  Download,
  FileDown,
  Check,
  Search,
  BookOpen,
  Activity,
  ListChecks,
  Wrench,
  Key,
  ShieldCheck,
  Flame,
  Radio
} from 'lucide-react';
import { 
  DISAGREEMENT_RESOLUTIONS, 
  DECISION_TABLE, 
  REWRITE_KILLERS, 
  RISK_REGISTER,
  MASTER_BUILD_CONTRACT_SECTIONS,
  IMPLEMENTATION_STEPS_20,
  ACCEPTANCE_TESTS_12,
  PHASE1_SAFE_TOOLS
} from './auditData';
import { 
  downloadMarkdownSpec, 
  downloadJSONSpec, 
  exportProjectZip, 
  downloadFile,
  downloadAPKFile,
  downloadAPKZipFile 
} from './exporter';
import { AcceptanceTest } from './types';
import { AgentStudioLive } from './components/AgentStudioLive';
import { SrishtiLiveCompanion } from './components/SrishtiLiveCompanion';

export default function App() {
  const [activeTab, setActiveTab] = useState<'srishti' | 'live' | 'verdict' | 'contract' | 'tests' | 'steps' | 'tools' | 'conflicts' | 'killers' | 'pipeline' | 'decisions' | 'risks'>('srishti');
  const [filterCategory, setFilterCategory] = useState<string>('all');
  const [contractCategory, setContractCategory] = useState<string>('all');
  const [contractSearch, setContractSearch] = useState<string>('');
  const [downloadSuccess, setDownloadSuccess] = useState<'md' | 'json' | 'zip' | null>(null);
  const [isExportingZip, setIsExportingZip] = useState(false);
  
  // Interactive Pipeline Sandbox State
  const [simRisk, setSimRisk] = useState<'SAFE' | 'CONFIRM' | 'HIGH_RISK' | 'BLOCKED'>('SAFE');
  const [simStep, setSimStep] = useState<number>(0);
  const [simLog, setSimLog] = useState<string[]>([]);
  const [isSimRunning, setIsSimRunning] = useState<boolean>(false);

  // Acceptance Test Runner State
  const [selectedTest, setSelectedTest] = useState<AcceptanceTest>(ACCEPTANCE_TESTS_12[0]);
  const [runningTestId, setRunningTestId] = useState<number | null>(null);
  const [testOutputLogs, setTestOutputLogs] = useState<{ [testId: number]: string[] }>({});
  const [testStatus, setTestStatus] = useState<{ [testId: number]: 'IDLE' | 'RUNNING' | 'PASSED' | 'FAILED' }>({});

  const runAcceptanceTestSimulation = (test: AcceptanceTest) => {
    setRunningTestId(test.id);
    setTestStatus(prev => ({ ...prev, [test.id]: 'RUNNING' }));
    const logs: string[] = [`[${new Date().toLocaleTimeString()}] INITIATING ${test.code}: ${test.name}`];
    logs.push(`Target Subsystem: ${test.target}`);
    setTestOutputLogs(prev => ({ ...prev, [test.id]: [...logs] }));

    test.simSteps.forEach((step, index) => {
      setTimeout(() => {
        logs.push(`[${new Date().toLocaleTimeString()}] Step ${index + 1}/${test.simSteps.length}: ${step}`);
        setTestOutputLogs(prev => ({ ...prev, [test.id]: [...logs] }));

        if (index === test.simSteps.length - 1) {
          setTimeout(() => {
            logs.push(`[${new Date().toLocaleTimeString()}] VERIFICATION PASSED: ${test.expectedOutcome}`);
            logs.push(`[${new Date().toLocaleTimeString()}] RESULT: GREEN (TEST PASSED)`);
            setTestOutputLogs(prev => ({ ...prev, [test.id]: [...logs] }));
            setTestStatus(prev => ({ ...prev, [test.id]: 'PASSED' }));
            setRunningTestId(null);
          }, 300);
        }
      }, (index + 1) * 350);
    });
  };

  const runAllTests = () => {
    ACCEPTANCE_TESTS_12.forEach((test, idx) => {
      setTimeout(() => {
        runAcceptanceTestSimulation(test);
      }, idx * 1200);
    });
  };

  const runSimulation = (riskType: 'SAFE' | 'CONFIRM' | 'HIGH_RISK' | 'BLOCKED') => {
    setSimRisk(riskType);
    setSimStep(1);
    setIsSimRunning(true);
    const logs = [`[${new Date().toLocaleTimeString()}] Pipeline started: Intent extracted.`];
    
    setTimeout(() => {
      logs.push(`[${new Date().toLocaleTimeString()}] Capability matched → Model inference executed with strict GBNF grammar.`);
      setSimStep(2);
      setSimLog([...logs]);
    }, 400);

    setTimeout(() => {
      logs.push(`[${new Date().toLocaleTimeString()}] Tool Decision parsed: Name & Arguments validated against schema.`);
      setSimStep(3);
      setSimLog([...logs]);
    }, 800);

    setTimeout(() => {
      logs.push(`[${new Date().toLocaleTimeString()}] Risk Engine evaluated: Assigned risk = ${riskType}.`);
      setSimStep(4);
      setSimLog([...logs]);

      if (riskType === 'BLOCKED') {
        logs.push(`[${new Date().toLocaleTimeString()}] BLOCKED: Tool execution aborted. Error returned to Agent.`);
        setSimStep(7);
        setIsSimRunning(false);
        setSimLog([...logs]);
      } else if (riskType === 'HIGH_RISK') {
        logs.push(`[${new Date().toLocaleTimeString()}] HIGH_RISK: Step saved in Room DB. Waiting for Biometric / System Confirmation.`);
        setSimStep(5);
        setIsSimRunning(false);
        setSimLog([...logs]);
      } else if (riskType === 'CONFIRM') {
        logs.push(`[${new Date().toLocaleTimeString()}] CONFIRM: Step saved in Room DB. User approval required.`);
        setSimStep(5);
        setIsSimRunning(false);
        setSimLog([...logs]);
      } else {
        logs.push(`[${new Date().toLocaleTimeString()}] SAFE: Auto-executing tool via Android native API.`);
        setSimStep(6);
        setSimLog([...logs]);
        setTimeout(() => {
          logs.push(`[${new Date().toLocaleTimeString()}] Execution verified. Task state updated to COMPLETED.`);
          setSimStep(8);
          setIsSimRunning(false);
          setSimLog([...logs]);
        }, 500);
      }
    }, 1200);
  };

  const resetSimulation = () => {
    setSimStep(0);
    setSimLog([]);
    setIsSimRunning(false);
  };

  const filteredDecisions = DECISION_TABLE.filter(item => {
    if (filterCategory === 'buildNow') return item.buildNow;
    if (filterCategory === 'designNow') return item.designNow;
    if (filterCategory === 'later') return item.later;
    if (filterCategory === 'reject') return item.reject;
    return true;
  });

  const filteredContractSections = MASTER_BUILD_CONTRACT_SECTIONS.filter(s => {
    const matchesCat = contractCategory === 'all' || s.category === contractCategory;
    const matchesSearch = contractSearch === '' || 
      s.title.toLowerCase().includes(contractSearch.toLowerCase()) ||
      s.summary.toLowerCase().includes(contractSearch.toLowerCase()) ||
      s.mandatoryRules.some(r => r.toLowerCase().includes(contractSearch.toLowerCase()));
    return matchesCat && matchesSearch;
  });

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 flex flex-col font-sans selection:bg-cyan-500 selection:text-slate-950">
      {/* Top Header */}
      <header className="border-b border-slate-800 bg-slate-900/80 backdrop-blur px-6 py-4 sticky top-0 z-50">
        <div className="max-w-7xl mx-auto flex flex-col md:flex-row md:items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-cyan-500/10 border border-cyan-500/30 flex items-center justify-center text-cyan-400 font-mono font-bold text-lg shadow-sm">
              OD
            </div>
            <div>
              <div className="flex items-center gap-2 flex-wrap">
                <h1 className="text-xl font-bold tracking-tight text-white">OpenDroid</h1>
                <span className="text-xs px-2 py-0.5 rounded-full bg-cyan-500/20 text-cyan-300 font-medium border border-cyan-500/30">
                  Master Build Contract
                </span>
                <span className="text-xs px-2 py-0.5 rounded-full bg-emerald-500/20 text-emerald-300 font-medium border border-emerald-500/30">
                  READY FOR GEMINI
                </span>
              </div>
              <p className="text-xs text-slate-400">Phase 1 Frozen Specification • 39 Directives • 12 Acceptance Tests • 4 Safe Tools</p>
            </div>
          </div>

          <div className="flex items-center gap-2">
            {/* Quick Download Buttons */}
            <div className="flex items-center bg-slate-800/90 border border-slate-700/80 rounded-lg p-0.5">
              <button
                id="btn-download-apk"
                onClick={async () => {
                  await downloadAPKFile('Srishti3.0.apk');
                }}
                className="flex items-center gap-1.5 px-3 py-1.5 rounded-md text-xs font-bold bg-cyan-500 text-slate-950 hover:bg-cyan-400 active:scale-95 transition cursor-pointer shadow-sm"
                title="Download Srishti 3.0 Android APK directly"
              >
                <Download className="w-3.5 h-3.5" />
                <span>Download Srishti3.0.apk</span>
              </button>
              <button
                id="btn-download-apk-zip"
                onClick={async () => {
                  await downloadAPKZipFile('Srishti3.0-APK.zip');
                }}
                className="flex items-center gap-1.5 px-3 py-1.5 rounded-md text-xs font-semibold text-emerald-300 hover:text-white hover:bg-emerald-900/40 active:scale-95 transition cursor-pointer"
                title="Download APK Bundle ZIP (includes APK, checksums, and README)"
              >
                <Download className="w-3.5 h-3.5 text-emerald-400" />
                <span>Srishti3.0-APK.zip</span>
              </button>
              <button
                id="btn-download-project-zip"
                onClick={async () => {
                  try {
                    setIsExportingZip(true);
                    const { blob, filename } = await exportProjectZip();
                    downloadFile(blob, filename, 'application/zip');
                    setDownloadSuccess('zip');
                    setTimeout(() => setDownloadSuccess(null), 3000);
                  } finally {
                    setIsExportingZip(false);
                  }
                }}
                disabled={isExportingZip}
                className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-md text-xs font-medium text-slate-300 hover:text-white hover:bg-slate-700 transition cursor-pointer disabled:opacity-60"
                title="Download Real Srishti 3.0 Source Code ZIP Archive"
              >
                {isExportingZip ? (
                  <>
                    <span className="w-3.5 h-3.5 border-2 border-slate-950 border-t-transparent rounded-full animate-spin" />
                    <span>Zipping...</span>
                  </>
                ) : downloadSuccess === 'zip' ? (
                  <>
                    <Check className="w-3.5 h-3.5" />
                    <span>Source ZIP Saved!</span>
                  </>
                ) : (
                  <>
                    <FileDown className="w-3.5 h-3.5 text-slate-400" />
                    <span>Source ZIP</span>
                  </>
                )}
              </button>
              <button
                id="btn-download-md"
                onClick={() => {
                  downloadMarkdownSpec();
                  setDownloadSuccess('md');
                  setTimeout(() => setDownloadSuccess(null), 2500);
                }}
                className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-md text-xs font-semibold text-slate-300 hover:text-white hover:bg-slate-700 transition cursor-pointer"
                title="Download Full Specification as Markdown (.md)"
              >
                {downloadSuccess === 'md' ? (
                  <>
                    <Check className="w-3.5 h-3.5" />
                    <span>Saved .md!</span>
                  </>
                ) : (
                  <>
                    <FileDown className="w-3.5 h-3.5 text-slate-400" />
                    <span>Spec (.md)</span>
                  </>
                )}
              </button>
              <button
                id="btn-download-json"
                onClick={() => {
                  downloadJSONSpec();
                  setDownloadSuccess('json');
                  setTimeout(() => setDownloadSuccess(null), 2500);
                }}
                className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-md text-xs font-medium text-slate-300 hover:text-white hover:bg-slate-700 transition cursor-pointer"
                title="Download Structured Architecture Data as JSON (.json)"
              >
                {downloadSuccess === 'json' ? (
                  <span className="text-emerald-400">Saved .json!</span>
                ) : (
                  <>
                    <FileDown className="w-3.5 h-3.5 text-slate-400" />
                    <span>.JSON</span>
                  </>
                )}
              </button>
            </div>
          </div>
        </div>

        {/* Navigation Tabs Bar */}
        <div className="max-w-7xl mx-auto mt-3 pt-3 border-t border-slate-800/60 flex items-center gap-1.5 overflow-x-auto pb-1">
          <button
            onClick={() => setActiveTab('srishti')}
            className={`px-3 py-1.5 rounded-lg text-xs font-medium transition cursor-pointer whitespace-nowrap flex items-center gap-1.5 ${activeTab === 'srishti' ? 'bg-amber-400 text-slate-950 font-bold shadow-md shadow-amber-400/20' : 'bg-slate-800 text-amber-300 hover:bg-slate-700 border border-amber-500/30'}`}
          >
            <Sparkles className="w-3.5 h-3.5" />
            <span>Srishti 3.0 Companion</span>
          </button>
          <button
            onClick={() => setActiveTab('live')}
            className={`px-3 py-1.5 rounded-lg text-xs font-medium transition cursor-pointer whitespace-nowrap flex items-center gap-1.5 ${activeTab === 'live' ? 'bg-cyan-500 text-slate-950 font-bold shadow-sm shadow-cyan-500/20' : 'bg-slate-800 text-cyan-300 hover:bg-slate-700 border border-cyan-500/30'}`}
          >
            <Zap className="w-3.5 h-3.5" />
            <span>AgentCore HUD</span>
          </button>
          <button
            onClick={() => setActiveTab('verdict')}
            className={`px-3 py-1.5 rounded-lg text-xs font-medium transition cursor-pointer whitespace-nowrap ${activeTab === 'verdict' ? 'bg-cyan-500 text-slate-950 font-bold' : 'bg-slate-800 text-slate-300 hover:bg-slate-700'}`}
          >
            Verdict & Architecture
          </button>
          <button
            onClick={() => setActiveTab('contract')}
            className={`px-3 py-1.5 rounded-lg text-xs font-medium transition cursor-pointer whitespace-nowrap ${activeTab === 'contract' ? 'bg-amber-400 text-slate-950 font-bold' : 'bg-slate-800 text-amber-300 hover:bg-slate-700 border border-amber-500/30'}`}
          >
            39 Master Directives
          </button>
          <button
            onClick={() => setActiveTab('tests')}
            className={`px-3 py-1.5 rounded-lg text-xs font-medium transition cursor-pointer whitespace-nowrap ${activeTab === 'tests' ? 'bg-cyan-500 text-slate-950 font-bold' : 'bg-slate-800 text-slate-300 hover:bg-slate-700'}`}
          >
            12 Acceptance Tests
          </button>
          <button
            onClick={() => setActiveTab('steps')}
            className={`px-3 py-1.5 rounded-lg text-xs font-medium transition cursor-pointer whitespace-nowrap ${activeTab === 'steps' ? 'bg-cyan-500 text-slate-950 font-bold' : 'bg-slate-800 text-slate-300 hover:bg-slate-700'}`}
          >
            20-Step Implementation
          </button>
          <button
            onClick={() => setActiveTab('tools')}
            className={`px-3 py-1.5 rounded-lg text-xs font-medium transition cursor-pointer whitespace-nowrap ${activeTab === 'tools' ? 'bg-cyan-500 text-slate-950 font-bold' : 'bg-slate-800 text-slate-300 hover:bg-slate-700'}`}
          >
            4 Safe Native Tools
          </button>
          <button
            onClick={() => setActiveTab('conflicts')}
            className={`px-3 py-1.5 rounded-lg text-xs font-medium transition cursor-pointer whitespace-nowrap ${activeTab === 'conflicts' ? 'bg-cyan-500 text-slate-950 font-bold' : 'bg-slate-800 text-slate-300 hover:bg-slate-700'}`}
          >
            Claude vs DeepSeek
          </button>
          <button
            onClick={() => setActiveTab('killers')}
            className={`px-3 py-1.5 rounded-lg text-xs font-medium transition cursor-pointer whitespace-nowrap ${activeTab === 'killers' ? 'bg-cyan-500 text-slate-950 font-bold' : 'bg-slate-800 text-slate-300 hover:bg-slate-700'}`}
          >
            10 Rewrite Killers
          </button>
          <button
            onClick={() => setActiveTab('pipeline')}
            className={`px-3 py-1.5 rounded-lg text-xs font-medium transition cursor-pointer whitespace-nowrap ${activeTab === 'pipeline' ? 'bg-cyan-500 text-slate-950 font-bold' : 'bg-slate-800 text-slate-300 hover:bg-slate-700'}`}
          >
            Pipeline Sandbox
          </button>
          <button
            onClick={() => setActiveTab('decisions')}
            className={`px-3 py-1.5 rounded-lg text-xs font-medium transition cursor-pointer whitespace-nowrap ${activeTab === 'decisions' ? 'bg-cyan-500 text-slate-950 font-bold' : 'bg-slate-800 text-slate-300 hover:bg-slate-700'}`}
          >
            Decision Matrix
          </button>
          <button
            onClick={() => setActiveTab('risks')}
            className={`px-3 py-1.5 rounded-lg text-xs font-medium transition cursor-pointer whitespace-nowrap ${activeTab === 'risks' ? 'bg-cyan-500 text-slate-950 font-bold' : 'bg-slate-800 text-slate-300 hover:bg-slate-700'}`}
          >
            Risk Register
          </button>
        </div>
      </header>

      {/* Main Content Area */}
      <main className="flex-1 max-w-7xl w-full mx-auto p-6 space-y-6">
        
        {/* TAB 0: SRISHTI 3.0 LIVE COMPANION */}
        {activeTab === 'srishti' && (
          <SrishtiLiveCompanion />
        )}

        {/* TAB 0.5: LIVE AGENT STUDIO HUD */}
        {activeTab === 'live' && (
          <AgentStudioLive />
        )}

        {/* TAB 1: VERDICT & ARCHITECTURE */}
        {activeTab === 'verdict' && (
          <div className="space-y-6">
            {/* Final Verdict Banner */}
            <div className="p-6 rounded-2xl bg-gradient-to-r from-emerald-950/60 to-slate-900 border border-emerald-500/30">
              <div className="flex flex-col md:flex-row md:items-center justify-between gap-6">
                <div className="flex items-start gap-4">
                  <div className="p-3 rounded-xl bg-emerald-500/20 text-emerald-400 shrink-0">
                    <CheckCircle2 className="w-8 h-8" />
                  </div>
                  <div className="space-y-2">
                    <div className="flex items-center gap-3">
                      <span className="text-xs font-bold uppercase tracking-wider text-emerald-400 bg-emerald-500/10 px-2.5 py-1 rounded-md border border-emerald-500/20">
                        Final Committee Verdict
                      </span>
                      <h2 className="text-2xl font-bold text-white tracking-tight">READY FOR GEMINI</h2>
                    </div>
                    <p className="text-slate-300 text-sm leading-relaxed max-w-3xl">
                      The architecture has been rigorously audited, reconciled between Claude and DeepSeek reviews, and hardened against Android 14–16 lifecycle constraints, out-of-process C++ crashes, and prompt injection vectors.
                    </p>
                    <p className="text-xs font-mono text-emerald-300 bg-emerald-950/50 p-2.5 rounded-lg border border-emerald-900/60 inline-block">
                      &quot;No additional architectural subsystem should be added during Phase 1 unless a blocking requirement is discovered.&quot;
                    </p>
                  </div>
                </div>

                <div className="flex flex-col sm:flex-row md:flex-col gap-2 shrink-0">
                  <button
                    id="btn-verdict-download-md"
                    onClick={() => {
                      downloadMarkdownSpec();
                      setDownloadSuccess('md');
                      setTimeout(() => setDownloadSuccess(null), 2500);
                    }}
                    className="flex items-center justify-center gap-2 px-4 py-2.5 rounded-xl bg-cyan-500 hover:bg-cyan-400 text-slate-950 font-bold text-xs transition cursor-pointer shadow-lg shadow-cyan-500/10"
                  >
                    <Download className="w-4 h-4" />
                    <span>Download Master Contract (.md)</span>
                  </button>
                  <button
                    id="btn-verdict-download-json"
                    onClick={() => {
                      downloadJSONSpec();
                      setDownloadSuccess('json');
                      setTimeout(() => setDownloadSuccess(null), 2500);
                    }}
                    className="flex items-center justify-center gap-2 px-4 py-2 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-200 font-medium text-xs border border-slate-700 transition cursor-pointer"
                  >
                    <FileDown className="w-4 h-4 text-slate-400" />
                    <span>Export JSON Data (.json)</span>
                  </button>
                </div>
              </div>
            </div>

            {/* End-to-End Autonomous Agent Loop Diagram */}
            <div className="p-6 rounded-2xl bg-slate-900 border border-slate-800 space-y-4">
              <h3 className="text-base font-bold text-white flex items-center gap-2">
                <Layers className="w-5 h-5 text-cyan-400" />
                <span>The Frozen Phase 1 End-to-End Autonomous Loop</span>
              </h3>
              <p className="text-xs text-slate-400">
                The primary objective of Phase 1 is proving this single deterministic, recoverable loop across the Android OS boundary:
              </p>
              
              <div className="grid grid-cols-2 md:grid-cols-5 gap-3 pt-2">
                <div className="p-3.5 rounded-xl bg-slate-950 border border-slate-800 flex flex-col items-center text-center space-y-1">
                  <span className="text-[10px] font-bold text-cyan-400 font-mono">STEP 1</span>
                  <Smartphone className="w-5 h-5 text-slate-300" />
                  <span className="text-xs font-semibold text-slate-200">USER REQUEST</span>
                  <span className="text-[10px] text-slate-400">Intent & Bounded Context</span>
                </div>
                <div className="p-3.5 rounded-xl bg-slate-950 border border-slate-800 flex flex-col items-center text-center space-y-1">
                  <span className="text-[10px] font-bold text-cyan-400 font-mono">STEP 2</span>
                  <Cpu className="w-5 h-5 text-purple-400" />
                  <span className="text-xs font-semibold text-slate-200">AgentCore</span>
                  <span className="text-[10px] text-slate-400">In-Process Coordinator</span>
                </div>
                <div className="p-3.5 rounded-xl bg-slate-950 border border-slate-800 flex flex-col items-center text-center space-y-1">
                  <span className="text-[10px] font-bold text-cyan-400 font-mono">STEP 3</span>
                  <Radio className="w-5 h-5 text-amber-400" />
                  <span className="text-xs font-semibold text-slate-200">InferenceEngine</span>
                  <span className="text-[10px] text-slate-400">:inference AIDL / Cloud</span>
                </div>
                <div className="p-3.5 rounded-xl bg-slate-950 border border-slate-800 flex flex-col items-center text-center space-y-1">
                  <span className="text-[10px] font-bold text-cyan-400 font-mono">STEP 4</span>
                  <ShieldCheck className="w-5 h-5 text-emerald-400" />
                  <span className="text-xs font-semibold text-slate-200">Schema & Risk Gate</span>
                  <span className="text-[10px] text-slate-400">Pure 4-Tier Evaluation</span>
                </div>
                <div className="p-3.5 rounded-xl bg-slate-950 border border-slate-800 flex flex-col items-center text-center space-y-1 col-span-2 md:col-span-1">
                  <span className="text-[10px] font-bold text-cyan-400 font-mono">STEP 5</span>
                  <Wrench className="w-5 h-5 text-cyan-400" />
                  <span className="text-xs font-semibold text-slate-200">Native Execution</span>
                  <span className="text-[10px] text-slate-400">Verify & Room DB State</span>
                </div>
              </div>
            </div>

            {/* Architecture Metrics Grid */}
            <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
              <div className="p-5 rounded-xl bg-slate-900 border border-slate-800 space-y-3">
                <div className="flex items-center gap-2 text-cyan-400 font-semibold text-sm">
                  <Cpu className="w-4 h-4" />
                  <span>Inference Runtime Isolation</span>
                </div>
                <p className="text-slate-300 text-xs leading-relaxed">
                  <strong className="text-white">llama.cpp</strong> hosted strictly in an isolated Android Service (<code className="text-cyan-300 font-mono">:inference</code>) over AIDL/IPC with watchdog supervision. Main app survives native SIGSEGV and OOM kills.
                </p>
                <div className="text-[11px] text-slate-400 font-mono bg-slate-950 p-2 rounded border border-slate-800">
                  Target: 7–8GB RAM (Qwen 1.5B Q4_K_M ≤ 1.8GB)
                </div>
              </div>

              <div className="p-5 rounded-xl bg-slate-900 border border-slate-800 space-y-3">
                <div className="flex items-center gap-2 text-amber-400 font-semibold text-sm">
                  <Database className="w-4 h-4" />
                  <span>Room 8-State Task Machine</span>
                </div>
                <p className="text-slate-300 text-xs leading-relaxed">
                  Persistent Room SQLite state machine (<code className="text-amber-300 font-mono">CREATED → ANALYZING → PLANNED → WAITING_CONFIRMATION → EXECUTING → VERIFYING → COMPLETED</code>) survives Android Low Memory Killer (LMK).
                </p>
                <div className="text-[11px] text-slate-400 font-mono bg-slate-950 p-2 rounded border border-slate-800">
                  Idempotency: UUIDv5(task + step + args)
                </div>
              </div>

              <div className="p-5 rounded-xl bg-slate-900 border border-slate-800 space-y-3">
                <div className="flex items-center gap-2 text-emerald-400 font-semibold text-sm">
                  <Lock className="w-4 h-4" />
                  <span>Security & Zero Code Bloat</span>
                </div>
                <p className="text-slate-300 text-xs leading-relaxed">
                  Deterministic 4-Tier Risk Engine (<code className="text-emerald-300 font-mono">SAFE, CONFIRM, HIGH_RISK, BLOCKED</code>). Pure-function gate. No fake stubs. Secrets in Android Keystore with redacted logging.
                </p>
                <div className="text-[11px] text-slate-400 font-mono bg-slate-950 p-2 rounded border border-slate-800">
                  External text tagged &lt;untrusted_content&gt;
                </div>
              </div>
            </div>
          </div>
        )}

        {/* TAB 2: 39 MASTER DIRECTIVES */}
        {activeTab === 'contract' && (
          <div className="space-y-6">
            <div className="p-6 rounded-2xl bg-gradient-to-r from-amber-950/40 via-slate-900 to-slate-900 border border-amber-500/30 space-y-4">
              <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
                <div className="flex items-center gap-3">
                  <div className="p-3 rounded-xl bg-amber-500/20 text-amber-400 shrink-0">
                    <Lock className="w-8 h-8" />
                  </div>
                  <div>
                    <span className="text-xs font-bold uppercase tracking-wider text-amber-400 bg-amber-500/10 px-2.5 py-0.5 rounded border border-amber-500/20">
                      Mandatory Code Generation Policy
                    </span>
                    <h2 className="text-2xl font-bold text-white tracking-tight">39 MASTER DIRECTIVES</h2>
                  </div>
                </div>

                <div className="flex items-center gap-2">
                  <button
                    onClick={downloadMarkdownSpec}
                    className="flex items-center gap-2 px-3.5 py-2 rounded-xl bg-amber-400 hover:bg-amber-300 text-slate-950 font-bold text-xs transition cursor-pointer shadow-md"
                  >
                    <Download className="w-4 h-4" />
                    <span>Download Master Contract (.md)</span>
                  </button>
                </div>
              </div>
              
              <p className="text-slate-300 text-xs leading-relaxed max-w-4xl">
                Every code generator and engineer must adhere strictly to these 39 directives. <strong>No scope expansion, no unrequested background services, no fake mocks, and no arbitrary shell executions.</strong>
              </p>
            </div>

            {/* Filter and Search Bar */}
            <div className="flex flex-col sm:flex-row items-center justify-between gap-3 bg-slate-900 p-3 rounded-xl border border-slate-800">
              <div className="flex items-center gap-2 w-full sm:w-auto">
                <Search className="w-4 h-4 text-slate-400" />
                <input
                  type="text"
                  placeholder="Search directives, tools, rules..."
                  value={contractSearch}
                  onChange={(e) => setContractSearch(e.target.value)}
                  className="bg-slate-950 border border-slate-700/60 rounded-lg px-3 py-1.5 text-xs text-white placeholder:text-slate-500 focus:outline-none focus:border-cyan-500 w-full sm:w-64"
                />
              </div>

              <div className="flex items-center gap-1.5 overflow-x-auto w-full sm:w-auto pb-1 sm:pb-0">
                {(['all', 'Core Directives', 'Inference & Isolation', 'Tooling & Safety', 'State & Persistence', 'Testing & Delivery'] as const).map(cat => (
                  <button
                    key={cat}
                    onClick={() => setContractCategory(cat)}
                    className={`px-2.5 py-1 rounded-md text-[11px] font-medium transition cursor-pointer whitespace-nowrap ${contractCategory === cat ? 'bg-amber-400 text-slate-950 font-bold' : 'bg-slate-800 text-slate-300 hover:bg-slate-700'}`}
                  >
                    {cat === 'all' ? 'All (39)' : cat}
                  </button>
                ))}
              </div>
            </div>

            {/* Directives Cards List */}
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {filteredContractSections.map((sec) => (
                <div key={sec.sectionNumber} className="p-4 rounded-xl bg-slate-900 border border-slate-800 hover:border-slate-700 transition space-y-3">
                  <div className="flex items-center justify-between gap-2">
                    <div className="flex items-center gap-2">
                      <span className="text-xs font-mono font-bold px-2 py-0.5 rounded bg-slate-800 text-cyan-400 border border-slate-700">
                        §{sec.sectionNumber}
                      </span>
                      <h4 className="text-sm font-bold text-white">{sec.title}</h4>
                    </div>
                    <span className="text-[10px] px-2 py-0.5 rounded bg-slate-950 text-slate-400 border border-slate-800">
                      {sec.category}
                    </span>
                  </div>

                  <p className="text-xs text-slate-300 leading-relaxed">
                    {sec.summary}
                  </p>

                  <div className="space-y-1.5 pt-1">
                    <span className="text-[10px] font-semibold text-amber-400 uppercase tracking-wider">Mandatory Rules:</span>
                    <ul className="space-y-1">
                      {sec.mandatoryRules.map((r, i) => (
                        <li key={i} className="text-[11px] text-slate-300 flex items-start gap-1.5">
                          <span className="text-cyan-400 font-bold">•</span>
                          <span>{r}</span>
                        </li>
                      ))}
                    </ul>
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* TAB 3: 12 ACCEPTANCE TESTS RUNNER */}
        {activeTab === 'tests' && (
          <div className="space-y-6">
            <div className="p-6 rounded-2xl bg-slate-900 border border-slate-800 space-y-4">
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
                <div>
                  <span className="text-xs font-bold uppercase tracking-wider text-cyan-400 bg-cyan-500/10 px-2.5 py-0.5 rounded border border-cyan-500/20">
                    Verification Suite
                  </span>
                  <h3 className="text-xl font-bold text-white mt-1">12 Mandatory Acceptance Tests</h3>
                  <p className="text-xs text-slate-400 mt-1">
                    The acceptance test suite proves agent reliability across local inference, GBNF grammar constraints, Room persistence, crash survival, and LMK recovery.
                  </p>
                </div>

                <div className="flex items-center gap-2">
                  <button
                    onClick={runAllTests}
                    disabled={runningTestId !== null}
                    className="flex items-center gap-2 px-4 py-2 rounded-xl bg-cyan-500 hover:bg-cyan-400 text-slate-950 font-bold text-xs transition cursor-pointer disabled:opacity-50"
                  >
                    <Play className="w-4 h-4" />
                    <span>Run All 12 Tests</span>
                  </button>
                </div>
              </div>
            </div>

            {/* Test Selection and Console Grid */}
            <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
              {/* Test List (5 cols) */}
              <div className="lg:col-span-5 space-y-2">
                {ACCEPTANCE_TESTS_12.map((test) => {
                  const status = testStatus[test.id] || 'IDLE';
                  const isSelected = selectedTest.id === test.id;

                  return (
                    <div
                      key={test.id}
                      onClick={() => setSelectedTest(test)}
                      className={`p-3.5 rounded-xl border transition cursor-pointer ${isSelected ? 'bg-slate-900 border-cyan-500 shadow-md' : 'bg-slate-900/60 border-slate-800 hover:bg-slate-900'}`}
                    >
                      <div className="flex items-center justify-between gap-2">
                        <div className="flex items-center gap-2">
                          <span className="text-xs font-mono font-bold text-cyan-400">
                            {test.code}
                          </span>
                          <span className="text-xs font-bold text-white truncate max-w-[180px]">
                            {test.name}
                          </span>
                        </div>

                        <div>
                          {status === 'PASSED' && (
                            <span className="flex items-center gap-1 text-[10px] font-bold text-emerald-400 bg-emerald-950/60 px-2 py-0.5 rounded border border-emerald-800">
                              <CheckCircle2 className="w-3 h-3" /> PASSED
                            </span>
                          )}
                          {status === 'RUNNING' && (
                            <span className="flex items-center gap-1 text-[10px] font-bold text-cyan-400 bg-cyan-950/60 px-2 py-0.5 rounded border border-cyan-800 animate-pulse">
                              <Activity className="w-3 h-3 animate-spin" /> RUNNING
                            </span>
                          )}
                          {status === 'IDLE' && (
                            <span className="text-[10px] text-slate-500">READY</span>
                          )}
                        </div>
                      </div>
                      <p className="text-[11px] text-slate-400 mt-1 line-clamp-1">
                        {test.target}
                      </p>
                    </div>
                  );
                })}
              </div>

              {/* Test Detail & Terminal Execution View (7 cols) */}
              <div className="lg:col-span-7 space-y-4">
                <div className="p-5 rounded-xl bg-slate-900 border border-slate-800 space-y-3">
                  <div className="flex items-center justify-between">
                    <div>
                      <span className="text-xs font-mono text-cyan-400">{selectedTest.code}</span>
                      <h4 className="text-lg font-bold text-white">{selectedTest.name}</h4>
                    </div>
                    <button
                      onClick={() => runAcceptanceTestSimulation(selectedTest)}
                      disabled={runningTestId !== null}
                      className="flex items-center gap-1.5 px-3.5 py-1.5 rounded-lg bg-cyan-500 hover:bg-cyan-400 text-slate-950 font-bold text-xs transition cursor-pointer disabled:opacity-50"
                    >
                      <Play className="w-3.5 h-3.5" />
                      <span>Simulate Test</span>
                    </button>
                  </div>

                  <div className="space-y-1">
                    <span className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">Target:</span>
                    <p className="text-xs font-mono text-cyan-300 bg-slate-950 p-2 rounded border border-slate-800">
                      {selectedTest.target}
                    </p>
                  </div>

                  <div className="space-y-1">
                    <span className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">Description:</span>
                    <p className="text-xs text-slate-300">
                      {selectedTest.description}
                    </p>
                  </div>

                  <div className="space-y-1">
                    <span className="text-[10px] font-bold text-emerald-400 uppercase tracking-wider">Expected Outcome:</span>
                    <p className="text-xs text-emerald-300/90 bg-emerald-950/30 p-2 rounded border border-emerald-900/40">
                      {selectedTest.expectedOutcome}
                    </p>
                  </div>
                </div>

                {/* Simulated Test Output Terminal */}
                <div className="p-4 rounded-xl bg-slate-950 border border-slate-800 space-y-2 font-mono">
                  <div className="flex items-center justify-between text-xs text-slate-400 pb-1 border-b border-slate-800">
                    <span className="flex items-center gap-1.5">
                      <Terminal className="w-3.5 h-3.5 text-cyan-400" />
                      <span>Acceptance Test Execution Log</span>
                    </span>
                    <span className="text-[10px]">Android Device Simulation</span>
                  </div>

                  <div className="min-h-[160px] max-h-[220px] overflow-y-auto text-[11px] space-y-1 text-slate-300">
                    {testOutputLogs[selectedTest.id] && testOutputLogs[selectedTest.id].length > 0 ? (
                      testOutputLogs[selectedTest.id].map((log, idx) => (
                        <div key={idx} className="leading-tight">
                          <span className="text-slate-500 select-none">&gt; </span>
                          <span className={log.includes('PASSED') ? 'text-emerald-400 font-bold' : log.includes('INITIATING') ? 'text-cyan-300 font-bold' : 'text-slate-300'}>
                            {log}
                          </span>
                        </div>
                      ))
                    ) : (
                      <div className="text-slate-600 italic">Click &quot;Simulate Test&quot; to run verification on this test case...</div>
                    )}
                  </div>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* TAB 4: 20-STEP IMPLEMENTATION ROADMAP */}
        {activeTab === 'steps' && (
          <div className="space-y-6">
            <div className="p-6 rounded-2xl bg-slate-900 border border-slate-800 space-y-2">
              <span className="text-xs font-bold uppercase tracking-wider text-cyan-400 bg-cyan-500/10 px-2.5 py-0.5 rounded border border-cyan-500/20">
                Phase 1 Execution Order
              </span>
              <h3 className="text-xl font-bold text-white">20-Step Implementation Order</h3>
              <p className="text-xs text-slate-400">
                Build in small verified increments: compile → unit test → integration test → verify before moving to the next step.
              </p>
            </div>

            <div className="space-y-3">
              {IMPLEMENTATION_STEPS_20.map((step) => (
                <div key={step.step} className="p-4 rounded-xl bg-slate-900 border border-slate-800 flex flex-col md:flex-row md:items-start justify-between gap-4">
                  <div className="flex items-start gap-3">
                    <div className="w-8 h-8 rounded-lg bg-cyan-500/10 border border-cyan-500/30 flex items-center justify-center text-cyan-400 font-mono font-bold text-xs shrink-0">
                      #{step.step}
                    </div>
                    <div className="space-y-1">
                      <h4 className="text-sm font-bold text-white">{step.title}</h4>
                      <p className="text-xs text-slate-300">{step.description}</p>
                      
                      <div className="flex flex-wrap gap-1.5 pt-1">
                        {step.deliverables.map((del, i) => (
                          <span key={i} className="text-[10px] font-mono px-2 py-0.5 rounded bg-slate-950 text-slate-300 border border-slate-800">
                            {del}
                          </span>
                        ))}
                      </div>
                    </div>
                  </div>

                  <div className="text-right shrink-0 md:max-w-xs space-y-1">
                    <span className="text-[10px] font-semibold text-emerald-400 uppercase tracking-wider">Verification Check:</span>
                    <p className="text-[11px] text-slate-400 bg-slate-950 p-2 rounded border border-slate-800/80">
                      {step.verificationCheck}
                    </p>
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* TAB 5: 4 SAFE NATIVE TOOLS */}
        {activeTab === 'tools' && (
          <div className="space-y-6">
            <div className="p-6 rounded-2xl bg-slate-900 border border-slate-800 space-y-2">
              <span className="text-xs font-bold uppercase tracking-wider text-cyan-400 bg-cyan-500/10 px-2.5 py-0.5 rounded border border-cyan-500/20">
                Phase 1 Native Tools
              </span>
              <h3 className="text-xl font-bold text-white">4 Safe Android Native Tools</h3>
              <p className="text-xs text-slate-400">
                Phase 1 implements ONLY these four zero-risk tools. No Accessibility scraping, no WhatsApp automation, no arbitrary shell execution.
              </p>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              {PHASE1_SAFE_TOOLS.map((tool) => (
                <div key={tool.id} className="p-5 rounded-xl bg-slate-900 border border-slate-800 space-y-3">
                  <div className="flex items-center justify-between gap-2">
                    <div className="flex items-center gap-2">
                      <span className="text-xs font-mono font-bold text-cyan-400 bg-slate-950 px-2 py-1 rounded border border-slate-800">
                        {tool.name}
                      </span>
                    </div>
                    <span className="text-[10px] font-bold text-emerald-400 bg-emerald-950/60 px-2 py-0.5 rounded border border-emerald-800">
                      {tool.riskTier} (Auto-Execute)
                    </span>
                  </div>

                  <p className="text-xs text-slate-300">{tool.description}</p>

                  <div className="space-y-1">
                    <span className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">Android Native API:</span>
                    <code className="text-xs font-mono text-cyan-300 block bg-slate-950 p-2 rounded border border-slate-800">
                      {tool.apiUsed}
                    </code>
                  </div>

                  <div className="space-y-1">
                    <span className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">Input JSON Schema:</span>
                    <pre className="text-[11px] font-mono text-slate-300 bg-slate-950 p-2.5 rounded border border-slate-800 overflow-x-auto">
                      {JSON.stringify(tool.inputSchema, null, 2)}
                    </pre>
                  </div>

                  <div className="space-y-1">
                    <span className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">Verification Strategy:</span>
                    <p className="text-xs text-slate-400 bg-slate-950 p-2 rounded border border-slate-800">
                      {tool.verificationLogic}
                    </p>
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* TAB 6: CLAUDE VS DEEPSEEK */}
        {activeTab === 'conflicts' && (
          <div className="space-y-6">
            <div className="p-6 rounded-2xl bg-slate-900 border border-slate-800 space-y-2">
              <span className="text-xs font-bold uppercase tracking-wider text-cyan-400 bg-cyan-500/10 px-2.5 py-0.5 rounded border border-cyan-500/20">
                Adjudication Summary
              </span>
              <h3 className="text-xl font-bold text-white">5 Key Architectural Conflicts Resolved</h3>
              <p className="text-xs text-slate-400">
                Where Claude and DeepSeek disagreed during the architectural review, we synthesized optimal solutions to prevent technical debt.
              </p>
            </div>

            <div className="space-y-4">
              {DISAGREEMENT_RESOLUTIONS.map((res, idx) => (
                <div key={idx} className="p-5 rounded-xl bg-slate-900 border border-slate-800 space-y-3">
                  <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
                    <h4 className="text-sm font-bold text-white flex items-center gap-2">
                      <span className="text-xs font-mono text-cyan-400 bg-slate-950 px-2 py-0.5 rounded border border-slate-800">#{idx + 1}</span>
                      <span>{res.topic}</span>
                    </h4>
                    <span className={`text-xs px-2.5 py-0.5 rounded-full font-semibold border ${res.winner === 'Claude' ? 'bg-orange-500/20 text-orange-300 border-orange-500/30' : res.winner === 'DeepSeek' ? 'bg-blue-500/20 text-blue-300 border-blue-500/30' : 'bg-purple-500/20 text-purple-300 border-purple-500/30'}`}>
                      Winner: {res.winner}
                    </span>
                  </div>

                  <div className="grid grid-cols-1 md:grid-cols-2 gap-3 pt-1">
                    <div className="p-3 rounded-lg bg-slate-950 border border-slate-800 text-xs space-y-1">
                      <span className="text-[10px] font-bold text-orange-400 uppercase tracking-wider">Claude Review</span>
                      <p className="text-slate-300">{res.claudeView}</p>
                    </div>
                    <div className="p-3 rounded-lg bg-slate-950 border border-slate-800 text-xs space-y-1">
                      <span className="text-[10px] font-bold text-blue-400 uppercase tracking-wider">DeepSeek Review</span>
                      <p className="text-slate-300">{res.deepSeekView}</p>
                    </div>
                  </div>

                  <div className="p-3 rounded-lg bg-slate-950/80 border border-slate-800/80 text-xs space-y-1">
                    <span className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">Adjudication Rationale</span>
                    <p className="text-slate-300 leading-relaxed">{res.rationale}</p>
                  </div>

                  <div className="p-3 rounded-lg bg-emerald-950/30 border border-emerald-900/40 text-xs space-y-1">
                    <span className="text-[10px] font-bold text-emerald-400 uppercase tracking-wider">Frozen Architectural Decision</span>
                    <p className="text-emerald-300 font-mono">{res.frozenDecision}</p>
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* TAB 7: 10 REWRITE KILLERS */}
        {activeTab === 'killers' && (
          <div className="space-y-6">
            <div className="p-6 rounded-2xl bg-slate-900 border border-slate-800 space-y-2">
              <span className="text-xs font-bold uppercase tracking-wider text-rose-400 bg-rose-500/10 px-2.5 py-0.5 rounded border border-rose-500/20">
                Fatal Failure Modes
              </span>
              <h3 className="text-xl font-bold text-white">The 10 Architectural Rewrite Killers (Fixed Now)</h3>
              <p className="text-xs text-slate-400">
                Common catastrophic traps that destroy mobile AI projects midway, and the exact architectural defenses we designed to eliminate them.
              </p>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {REWRITE_KILLERS.map((killer) => (
                <div key={killer.id} className="p-4 rounded-xl bg-slate-900 border border-slate-800 space-y-2.5">
                  <div className="flex items-center gap-2">
                    <span className="text-xs font-mono font-bold text-rose-400 bg-rose-950/50 px-2 py-0.5 rounded border border-rose-900/50">
                      KILLER #{killer.id}
                    </span>
                    <h4 className="text-sm font-bold text-white">{killer.killer}</h4>
                  </div>
                  
                  <div className="p-2.5 rounded bg-slate-950 border border-slate-800/80 text-xs text-slate-300 space-y-1">
                    <span className="text-[10px] font-bold text-rose-400 uppercase tracking-wider">The Danger:</span>
                    <p>{killer.danger}</p>
                  </div>

                  <div className="p-2.5 rounded bg-emerald-950/30 border border-emerald-900/40 text-xs text-emerald-300 space-y-1">
                    <span className="text-[10px] font-bold text-emerald-400 uppercase tracking-wider">Architectural Fix:</span>
                    <p>{killer.architecturalFix}</p>
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* TAB 8: PIPELINE SANDBOX */}
        {activeTab === 'pipeline' && (
          <div className="space-y-6">
            <div className="p-6 rounded-2xl bg-slate-900 border border-slate-800 space-y-4">
              <div>
                <span className="text-xs font-bold uppercase tracking-wider text-cyan-400 bg-cyan-500/10 px-2.5 py-0.5 rounded border border-cyan-500/20">
                  Interactive Simulator
                </span>
                <h3 className="text-xl font-bold text-white mt-1">Tool Pipeline & Risk Engine Sandbox</h3>
                <p className="text-xs text-slate-400 mt-1">
                  Test how the 4-tier pure-function Risk Engine handles actions across the decoupled agent loop.
                </p>
              </div>

              <div className="flex flex-wrap items-center gap-3 pt-2">
                <button
                  onClick={() => runSimulation('SAFE')}
                  disabled={isSimRunning}
                  className="px-4 py-2 rounded-xl text-xs font-bold bg-emerald-500 text-slate-950 hover:bg-emerald-400 transition cursor-pointer disabled:opacity-50"
                >
                  Simulate SAFE (Flashlight ON)
                </button>
                <button
                  onClick={() => runSimulation('CONFIRM')}
                  disabled={isSimRunning}
                  className="px-4 py-2 rounded-xl text-xs font-bold bg-amber-400 text-slate-950 hover:bg-amber-300 transition cursor-pointer disabled:opacity-50"
                >
                  Simulate CONFIRM (Create Calendar Event)
                </button>
                <button
                  onClick={() => runSimulation('HIGH_RISK')}
                  disabled={isSimRunning}
                  className="px-4 py-2 rounded-xl text-xs font-bold bg-orange-500 text-slate-950 hover:bg-orange-400 transition cursor-pointer disabled:opacity-50"
                >
                  Simulate HIGH_RISK (Uninstall App)
                </button>
                <button
                  onClick={() => runSimulation('BLOCKED')}
                  disabled={isSimRunning}
                  className="px-4 py-2 rounded-xl text-xs font-bold bg-rose-500 text-slate-950 hover:bg-rose-400 transition cursor-pointer disabled:opacity-50"
                >
                  Simulate BLOCKED (Export Keystore)
                </button>
                <button
                  onClick={resetSimulation}
                  disabled={isSimRunning}
                  className="px-3 py-2 rounded-xl text-xs font-medium bg-slate-800 text-slate-300 hover:bg-slate-700 transition cursor-pointer disabled:opacity-50"
                >
                  Reset
                </button>
              </div>
            </div>

            {/* Simulation Steps Visualizer */}
            <div className="grid grid-cols-1 md:grid-cols-4 gap-3">
              <div className={`p-3 rounded-xl border text-xs space-y-1 ${simStep >= 1 ? 'bg-slate-900 border-cyan-500 text-cyan-300' : 'bg-slate-950 border-slate-800 text-slate-500'}`}>
                <span className="font-bold">1. Intent Dispatch</span>
                <p className="text-[11px]">User prompt parsed with context bounds.</p>
              </div>
              <div className={`p-3 rounded-xl border text-xs space-y-1 ${simStep >= 2 ? 'bg-slate-900 border-cyan-500 text-cyan-300' : 'bg-slate-950 border-slate-800 text-slate-500'}`}>
                <span className="font-bold">2. Grammar Inference</span>
                <p className="text-[11px]">llama.cpp constrained by GBNF grammar.</p>
              </div>
              <div className={`p-3 rounded-xl border text-xs space-y-1 ${simStep >= 3 ? 'bg-slate-900 border-cyan-500 text-cyan-300' : 'bg-slate-950 border-slate-800 text-slate-500'}`}>
                <span className="font-bold">3. Schema Validation</span>
                <p className="text-[11px]">Strict argument type checking.</p>
              </div>
              <div className={`p-3 rounded-xl border text-xs space-y-1 ${simStep >= 4 ? 'bg-slate-900 border-cyan-500 text-cyan-300' : 'bg-slate-950 border-slate-800 text-slate-500'}`}>
                <span className="font-bold">4. Risk Evaluation</span>
                <p className="text-[11px]">Pure function tier classification.</p>
              </div>
            </div>

            {/* Terminal Logs */}
            <div className="p-4 rounded-xl bg-slate-950 border border-slate-800 font-mono text-xs space-y-1 text-slate-300 min-h-[160px]">
              <div className="text-slate-500 text-[10px] pb-1 border-b border-slate-800 flex items-center justify-between">
                <span>AgentCore Execution Terminal Log</span>
                <span>Active Status: {isSimRunning ? 'PROCESSING' : simStep > 0 ? 'COMPLETE' : 'IDLE'}</span>
              </div>
              {simLog.length > 0 ? (
                simLog.map((log, i) => (
                  <div key={i} className="leading-relaxed">
                    <span className="text-slate-600">&gt; </span>
                    <span className={log.includes('BLOCKED') ? 'text-rose-400 font-bold' : log.includes('COMPLETED') ? 'text-emerald-400 font-bold' : 'text-slate-300'}>
                      {log}
                    </span>
                  </div>
                ))
              ) : (
                <div className="text-slate-600 italic pt-2">Click one of the buttons above to trace the pipeline...</div>
              )}
            </div>
          </div>
        )}

        {/* TAB 9: DECISION MATRIX */}
        {activeTab === 'decisions' && (
          <div className="space-y-6">
            <div className="p-6 rounded-2xl bg-slate-900 border border-slate-800 space-y-4">
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
                <div>
                  <span className="text-xs font-bold uppercase tracking-wider text-cyan-400 bg-cyan-500/10 px-2.5 py-0.5 rounded border border-cyan-500/20">
                    Architecture Scoping
                  </span>
                  <h3 className="text-xl font-bold text-white mt-1">Component Decision Matrix</h3>
                </div>

                <div className="flex items-center gap-1.5 overflow-x-auto pb-1 sm:pb-0">
                  {(['all', 'buildNow', 'designNow', 'later', 'reject'] as const).map((cat) => (
                    <button
                      key={cat}
                      onClick={() => setFilterCategory(cat)}
                      className={`px-3 py-1.5 rounded-lg text-xs font-medium transition cursor-pointer ${filterCategory === cat ? 'bg-cyan-500 text-slate-950 font-bold' : 'bg-slate-800 text-slate-300 hover:bg-slate-700'}`}
                    >
                      {cat === 'all' ? 'All (20)' : cat === 'buildNow' ? 'Build Now (12)' : cat === 'designNow' ? 'Design Now (3)' : cat === 'later' ? 'Later Phase (1)' : 'Rejected (4)'}
                    </button>
                  ))}
                </div>
              </div>
            </div>

            <div className="overflow-x-auto rounded-xl border border-slate-800 bg-slate-900">
              <table className="w-full text-left text-xs text-slate-300">
                <thead className="bg-slate-950 text-[10px] uppercase font-bold text-slate-400 border-b border-slate-800">
                  <tr>
                    <th className="p-3">Component / Subsystem</th>
                    <th className="p-3 text-center">Build (Ph 1)</th>
                    <th className="p-3 text-center">Design Now</th>
                    <th className="p-3 text-center">Later</th>
                    <th className="p-3 text-center">Reject</th>
                    <th className="p-3">Architectural Rationale</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-800/60 font-mono">
                  {filteredDecisions.map((d, i) => (
                    <tr key={i} className="hover:bg-slate-800/40 transition">
                      <td className="p-3 font-semibold text-white font-sans">{d.component}</td>
                      <td className="p-3 text-center">{d.buildNow ? <span className="text-emerald-400 font-bold">✓</span> : <span className="text-slate-600">—</span>}</td>
                      <td className="p-3 text-center">{d.designNow ? <span className="text-amber-400 font-bold">✓</span> : <span className="text-slate-600">—</span>}</td>
                      <td className="p-3 text-center">{d.later ? <span className="text-blue-400 font-bold">✓</span> : <span className="text-slate-600">—</span>}</td>
                      <td className="p-3 text-center">{d.reject ? <span className="text-rose-400 font-bold">✗</span> : <span className="text-slate-600">—</span>}</td>
                      <td className="p-3 text-slate-400 font-sans">{d.reason}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}

        {/* TAB 10: RISK REGISTER */}
        {activeTab === 'risks' && (
          <div className="space-y-6">
            <div className="p-6 rounded-2xl bg-slate-900 border border-slate-800 space-y-2">
              <span className="text-xs font-bold uppercase tracking-wider text-cyan-400 bg-cyan-500/10 px-2.5 py-0.5 rounded border border-cyan-500/20">
                Threat Modeling
              </span>
              <h3 className="text-xl font-bold text-white">Adversarial Risk Register</h3>
              <p className="text-xs text-slate-400">
                Proactive mitigations for process crashes, prompt injections, and Google Play policy violations.
              </p>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {RISK_REGISTER.map((risk) => (
                <div key={risk.id} className="p-4 rounded-xl bg-slate-900 border border-slate-800 space-y-2.5">
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-mono font-bold text-cyan-400 bg-slate-950 px-2 py-0.5 rounded border border-slate-800">
                      {risk.id}
                    </span>
                    <span className={`text-[10px] font-bold px-2 py-0.5 rounded border ${risk.severity === 'CRITICAL' ? 'bg-rose-500/20 text-rose-300 border-rose-500/30' : 'bg-amber-500/20 text-amber-300 border-amber-500/30'}`}>
                      {risk.severity} SEVERITY
                    </span>
                  </div>

                  <p className="text-xs font-bold text-white">{risk.risk}</p>

                  <div className="p-2.5 rounded bg-emerald-950/30 border border-emerald-900/40 text-xs text-emerald-300 space-y-1">
                    <span className="text-[10px] font-bold text-emerald-400 uppercase tracking-wider">Mitigation:</span>
                    <p>{risk.mitigation}</p>
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

      </main>

      {/* Floating Quick-Download Action Bar (Bottom Right) */}
      <div className="fixed bottom-6 right-6 z-40 flex items-center gap-2 bg-slate-900/95 backdrop-blur-md p-1.5 rounded-2xl border border-slate-700/80 shadow-2xl shadow-black/80">
        <button
          id="btn-floating-download-md"
          onClick={() => {
            downloadMarkdownSpec();
            setDownloadSuccess('md');
            setTimeout(() => setDownloadSuccess(null), 2500);
          }}
          className="flex items-center gap-2 px-3.5 py-2 rounded-xl bg-cyan-500 hover:bg-cyan-400 text-slate-950 font-bold text-xs transition cursor-pointer shadow-md"
          title="Download OpenDroid Master Build Contract (.md)"
        >
          {downloadSuccess === 'md' ? (
            <>
              <Check className="w-4 h-4" />
              <span>Saved Master Contract!</span>
            </>
          ) : (
            <>
              <Download className="w-4 h-4" />
              <span>Download Contract (.md)</span>
            </>
          )}
        </button>
        <button
          id="btn-floating-download-json"
          onClick={() => {
            downloadJSONSpec();
            setDownloadSuccess('json');
            setTimeout(() => setDownloadSuccess(null), 2500);
          }}
          className="flex items-center gap-1.5 px-3 py-2 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-300 hover:text-white font-medium text-xs border border-slate-700 transition cursor-pointer"
          title="Download Structured Architecture JSON Data (.json)"
        >
          {downloadSuccess === 'json' ? (
            <span className="text-emerald-400">Saved JSON!</span>
          ) : (
            <>
              <FileDown className="w-3.5 h-3.5 text-slate-400" />
              <span>.JSON</span>
            </>
          )}
        </button>
      </div>

      {/* Footer */}
      <footer className="border-t border-slate-800 bg-slate-950 px-6 py-6 text-xs text-slate-500">
        <div className="max-w-7xl mx-auto flex flex-col sm:flex-row items-center justify-between gap-4">
          <div>
            OpenDroid Architecture Review Committee • Phase 1 Master Build Contract • 2026 Frozen Specification
          </div>
          <div className="flex items-center gap-3">
            <button
              onClick={downloadMarkdownSpec}
              className="text-cyan-400 hover:text-cyan-300 underline font-medium cursor-pointer"
            >
              Download Full Markdown (.md)
            </button>
            <span>•</span>
            <button
              onClick={downloadJSONSpec}
              className="text-slate-400 hover:text-slate-200 underline font-medium cursor-pointer"
            >
              Export JSON (.json)
            </button>
          </div>
        </div>
      </footer>
    </div>
  );
}
