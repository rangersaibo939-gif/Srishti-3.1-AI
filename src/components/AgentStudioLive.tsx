import React, { useState, useEffect, useRef } from 'react';
import { 
  Play, 
  Square, 
  CheckCircle2, 
  AlertTriangle, 
  XCircle, 
  Activity, 
  ShieldCheck, 
  Terminal, 
  Cpu, 
  Database, 
  Zap, 
  Smartphone, 
  Lock, 
  Flame, 
  Volume2, 
  Battery, 
  AppWindow, 
  RefreshCw,
  Clock,
  Radio
} from 'lucide-react';
import { AgentCore } from '../core/agentCore';
import { RoomTaskRepository } from '../core/roomStore';
import { NativeToolExecutor } from '../core/tools';
import { InferenceEngine } from '../core/inference';
import { PersistedTask, TaskStatus, ToolCallRequest } from '../core/domain';

export const AgentStudioLive: React.FC = () => {
  const [prompt, setPrompt] = useState<string>('Turn on the flashlight');
  const [isRunning, setIsRunning] = useState<boolean>(false);
  const [activeTask, setActiveTask] = useState<PersistedTask | null>(null);
  const [taskHistory, setTaskHistory] = useState<PersistedTask[]>([]);
  const [logs, setLogs] = useState<string[]>([]);
  const [currentStatus, setCurrentStatus] = useState<TaskStatus | 'IDLE'>('IDLE');
  
  // Hardware status
  const [hardwareState, setHardwareState] = useState(NativeToolExecutor.getDeviceState());
  const [inferenceServiceStatus, setInferenceServiceStatus] = useState(InferenceEngine.getServiceStatus());

  // Confirmation dialog modal state
  const [confirmationReq, setConfirmationReq] = useState<{
    toolCall: ToolCallRequest;
    reason: string;
    onConfirm: () => void;
    onReject: () => void;
  } | null>(null);

  const logsEndRef = useRef<HTMLDivElement>(null);

  const refreshHistory = async () => {
    const all = await RoomTaskRepository.getAllTasks();
    setTaskHistory(all);
    setHardwareState(NativeToolExecutor.getDeviceState());
    setInferenceServiceStatus(InferenceEngine.getServiceStatus());
  };

  useEffect(() => {
    refreshHistory();
  }, []);

  useEffect(() => {
    logsEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [logs]);

  const handleExecute = async () => {
    if (!prompt.trim() || isRunning) return;

    setIsRunning(true);
    setCurrentStatus('CREATED');
    setLogs([`[${new Date().toLocaleTimeString()}] Starting execution for: "${prompt}"`]);

    try {
      const resultTask = await AgentCore.executeTask(prompt, {
        onLog: (msg) => setLogs(prev => [...prev, msg]),
        onStateChange: (st) => setCurrentStatus(st),
        onConfirmationRequired: (toolCall, reason, onConfirm, onReject) => {
          setConfirmationReq({
            toolCall,
            reason,
            onConfirm: () => {
              setConfirmationReq(null);
              onConfirm();
            },
            onReject: () => {
              setConfirmationReq(null);
              onReject();
            }
          });
        }
      });

      setActiveTask(resultTask);
      await refreshHistory();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : String(err);
      setLogs(prev => [...prev, `[ERROR] Execution halted: ${msg}`]);
      setCurrentStatus('FAILED');
    } finally {
      setIsRunning(false);
      setHardwareState(NativeToolExecutor.getDeviceState());
    }
  };

  const handleEmergencyStop = () => {
    AgentCore.triggerEmergencyStop();
    setLogs(prev => [...prev, `[${new Date().toLocaleTimeString()}] EMERGENCY STOP TRIGGERED BY USER`]);
    setIsRunning(false);
    setCurrentStatus('FAILED');
    setTimeout(() => {
      AgentCore.resetEmergencyStop();
    }, 1000);
  };

  const handleSimulateCrash = () => {
    InferenceEngine.triggerSimulatedCrash();
    setInferenceServiceStatus(InferenceEngine.getServiceStatus());
    setLogs(prev => [...prev, `[${new Date().toLocaleTimeString()}] SIMULATED NATIVE SIGSEGV IN :inference PROCESS`]);
    setLogs(prev => [...prev, `[${new Date().toLocaleTimeString()}] Android Watchdog detecting death recipient, restarting daemon...`]);
    setTimeout(() => {
      setInferenceServiceStatus(InferenceEngine.getServiceStatus());
      setLogs(prev => [...prev, `[${new Date().toLocaleTimeString()}] Isolated :inference process restored. AgentCore unaffected.`]);
    }, 1600);
  };

  return (
    <div className="space-y-6">
      {/* Confirmation Modal */}
      {confirmationReq && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 backdrop-blur-sm p-4">
          <div className="bg-slate-900 border-2 border-amber-500 rounded-2xl p-6 max-w-md w-full shadow-2xl space-y-4 animate-in fade-in zoom-in-95">
            <div className="flex items-center gap-3">
              <div className="p-2.5 rounded-xl bg-amber-500/20 text-amber-400">
                <AlertTriangle className="w-6 h-6" />
              </div>
              <div>
                <h4 className="text-base font-bold text-white">Confirmation Required</h4>
                <span className="text-xs font-mono text-amber-400">Action: {confirmationReq.toolCall.name}</span>
              </div>
            </div>

            <p className="text-xs text-slate-300 bg-slate-950 p-3 rounded-lg border border-slate-800">
              {confirmationReq.reason}
            </p>

            <div className="space-y-1">
              <span className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">Arguments:</span>
              <pre className="text-[11px] font-mono text-cyan-300 bg-slate-950 p-2 rounded border border-slate-800">
                {JSON.stringify(confirmationReq.toolCall.arguments, null, 2)}
              </pre>
            </div>

            <div className="flex items-center justify-end gap-2 pt-2">
              <button
                onClick={confirmationReq.onReject}
                className="px-4 py-2 rounded-xl text-xs font-semibold bg-slate-800 hover:bg-slate-700 text-slate-300 transition cursor-pointer"
              >
                Reject Action
              </button>
              <button
                onClick={confirmationReq.onConfirm}
                className="px-4 py-2 rounded-xl text-xs font-bold bg-amber-400 hover:bg-amber-300 text-slate-950 transition cursor-pointer"
              >
                Authorize & Execute
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Top Banner & Hardware HUD */}
      <div className="grid grid-cols-1 lg:grid-cols-4 gap-4">
        {/* Flashlight HUD */}
        <div className="p-4 rounded-xl bg-slate-900 border border-slate-800 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className={`p-2.5 rounded-lg ${hardwareState.flashlight ? 'bg-amber-500/20 text-amber-400 border border-amber-500/30' : 'bg-slate-800 text-slate-400'}`}>
              <Flame className="w-5 h-5" />
            </div>
            <div>
              <span className="text-[10px] text-slate-400 font-medium">Flashlight (Torch)</span>
              <div className="text-sm font-bold text-white">{hardwareState.flashlight ? 'ACTIVE (ON)' : 'OFF'}</div>
            </div>
          </div>
          <span className={`text-[10px] font-mono font-bold px-2 py-0.5 rounded ${hardwareState.flashlight ? 'bg-amber-500 text-slate-950' : 'bg-slate-800 text-slate-400'}`}>
            CameraManager
          </span>
        </div>

        {/* Media Volume HUD */}
        <div className="p-4 rounded-xl bg-slate-900 border border-slate-800 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="p-2.5 rounded-lg bg-cyan-500/10 text-cyan-400 border border-cyan-500/20">
              <Volume2 className="w-5 h-5" />
            </div>
            <div>
              <span className="text-[10px] text-slate-400 font-medium">Media Volume</span>
              <div className="text-sm font-bold text-white">{hardwareState.volume}%</div>
            </div>
          </div>
          <span className="text-[10px] font-mono text-cyan-400 bg-slate-950 px-2 py-0.5 rounded border border-slate-800">
            AudioManager
          </span>
        </div>

        {/* Battery HUD */}
        <div className="p-4 rounded-xl bg-slate-900 border border-slate-800 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="p-2.5 rounded-lg bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
              <Battery className="w-5 h-5" />
            </div>
            <div>
              <span className="text-[10px] text-slate-400 font-medium">Battery Level</span>
              <div className="text-sm font-bold text-white">{hardwareState.battery}% (Normal)</div>
            </div>
          </div>
          <span className="text-[10px] font-mono text-emerald-400 bg-slate-950 px-2 py-0.5 rounded border border-slate-800">
            BatteryManager
          </span>
        </div>

        {/* Inference Process HUD */}
        <div className="p-4 rounded-xl bg-slate-900 border border-slate-800 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className={`p-2.5 rounded-lg ${inferenceServiceStatus.alive ? 'bg-purple-500/10 text-purple-400 border border-purple-500/20' : 'bg-rose-500/20 text-rose-400 border border-rose-500/30'}`}>
              <Radio className="w-5 h-5" />
            </div>
            <div>
              <span className="text-[10px] text-slate-400 font-medium">:inference AIDL</span>
              <div className="text-sm font-bold text-white">{inferenceServiceStatus.alive ? 'ISOLATED ALIVE' : 'RECOVERING...'}</div>
            </div>
          </div>
          <button
            onClick={handleSimulateCrash}
            className="text-[10px] font-mono px-2 py-1 rounded bg-rose-950/60 hover:bg-rose-900 text-rose-300 border border-rose-800 transition cursor-pointer"
            title="Simulate Native C++ SIGSEGV in isolated process"
          >
            Crash :inf
          </button>
        </div>
      </div>

      {/* Main Execution Console */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
        {/* Left Column: Input, Quick Tests & State Machine (5 cols) */}
        <div className="lg:col-span-5 space-y-4">
          {/* User Request Input Box */}
          <div className="p-5 rounded-xl bg-slate-900 border border-slate-800 space-y-3">
            <div className="flex items-center justify-between">
              <label className="text-xs font-bold text-white uppercase tracking-wider flex items-center gap-2">
                <Smartphone className="w-4 h-4 text-cyan-400" />
                <span>User Request (Android Intent)</span>
              </label>
              <span className="text-[10px] font-mono text-slate-400 bg-slate-950 px-2 py-0.5 rounded border border-slate-800">
                Phase 1 Bounded Context
              </span>
            </div>

            <textarea
              rows={3}
              value={prompt}
              onChange={(e) => setPrompt(e.target.value)}
              placeholder="e.g. Turn on the flashlight, Set volume to 90%, Open Calculator..."
              className="w-full bg-slate-950 border border-slate-700/80 rounded-lg p-3 text-xs text-white placeholder:text-slate-500 focus:outline-none focus:border-cyan-500 font-sans"
            />

            {/* Quick Test Presets */}
            <div className="space-y-1">
              <span className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">Quick Presets:</span>
              <div className="flex flex-wrap gap-1.5">
                {[
                  { label: 'Flashlight ON', q: 'Turn on flashlight' },
                  { label: 'Flashlight OFF', q: 'Disable torch' },
                  { label: 'Volume 40%', q: 'Set media volume to 40%' },
                  { label: 'Volume 95% (CONFIRM)', q: 'Set media volume to 95%' },
                  { label: 'Battery Info', q: 'Check battery status' },
                  { label: 'Open Calculator', q: 'Launch calculator app' },
                  { label: 'Open Settings (HIGH_RISK)', q: 'Open com.android.settings' }
                ].map((p, i) => (
                  <button
                    key={i}
                    onClick={() => setPrompt(p.q)}
                    className="text-[11px] px-2.5 py-1 rounded bg-slate-950 hover:bg-slate-800 text-slate-300 border border-slate-800 transition cursor-pointer"
                  >
                    {p.label}
                  </button>
                ))}
              </div>
            </div>

            {/* Controls */}
            <div className="flex items-center gap-2 pt-2">
              <button
                onClick={handleExecute}
                disabled={isRunning}
                className="flex-1 flex items-center justify-center gap-2 px-4 py-2.5 rounded-xl bg-cyan-500 hover:bg-cyan-400 text-slate-950 font-bold text-xs transition cursor-pointer disabled:opacity-50 shadow-md"
              >
                {isRunning ? (
                  <>
                    <Activity className="w-4 h-4 animate-spin" />
                    <span>Executing Pipeline...</span>
                  </>
                ) : (
                  <>
                    <Play className="w-4 h-4" />
                    <span>Run AgentCore Loop</span>
                  </>
                )}
              </button>

              <button
                onClick={handleEmergencyStop}
                className="flex items-center gap-1.5 px-3.5 py-2.5 rounded-xl bg-rose-600 hover:bg-rose-500 text-white font-bold text-xs transition cursor-pointer shadow-md"
                title="Global Emergency Stop (Atomic cancellation)"
              >
                <Square className="w-3.5 h-3.5" />
                <span>HALT</span>
              </button>
            </div>
          </div>

          {/* 8-State Room Machine Visualizer */}
          <div className="p-5 rounded-xl bg-slate-900 border border-slate-800 space-y-3">
            <div className="flex items-center justify-between">
              <span className="text-xs font-bold text-white flex items-center gap-2">
                <Database className="w-4 h-4 text-amber-400" />
                <span>Room SQLite 8-State Task Lifecycle</span>
              </span>
              <span className={`text-[10px] font-mono font-bold px-2 py-0.5 rounded ${currentStatus === 'COMPLETED' ? 'bg-emerald-500/20 text-emerald-400 border border-emerald-500/30' : currentStatus === 'FAILED' ? 'bg-rose-500/20 text-rose-400 border border-rose-500/30' : currentStatus === 'IDLE' ? 'bg-slate-800 text-slate-400' : 'bg-cyan-500/20 text-cyan-400 border border-cyan-500/30 animate-pulse'}`}>
                {currentStatus}
              </span>
            </div>

            <div className="grid grid-cols-2 gap-2 text-[11px] font-mono">
              {[
                { st: 'CREATED', desc: 'Room row inserted' },
                { st: 'ANALYZING', desc: 'Context & GBNF decode' },
                { st: 'PLANNED', desc: 'UUIDv5 key generated' },
                { st: 'WAITING_CONFIRMATION', desc: 'User confirmation gate' },
                { st: 'EXECUTING', desc: 'Native Android API call' },
                { st: 'VERIFYING', desc: 'Hardware state check' },
                { st: 'COMPLETED', desc: 'Durable success written' },
                { st: 'FAILED', desc: 'Safe fail / aborted' }
              ].map(({ st, desc }) => {
                const isActive = currentStatus === st;
                return (
                  <div
                    key={st}
                    className={`p-2 rounded-lg border transition ${isActive ? 'bg-cyan-950/80 border-cyan-500 text-cyan-300 font-bold' : 'bg-slate-950 border-slate-800 text-slate-400'}`}
                  >
                    <div className="flex items-center justify-between">
                      <span>{st}</span>
                      {isActive && <CheckCircle2 className="w-3 h-3 text-cyan-400" />}
                    </div>
                    <div className="text-[9px] text-slate-500 font-sans mt-0.5">{desc}</div>
                  </div>
                );
              })}
            </div>
          </div>
        </div>

        {/* Right Column: Real-time Terminal Log & Persistent Task Record (7 cols) */}
        <div className="lg:col-span-7 space-y-4">
          {/* Live Execution Terminal */}
          <div className="p-4 rounded-xl bg-slate-950 border border-slate-800 space-y-2 font-mono flex flex-col h-[340px]">
            <div className="flex items-center justify-between text-xs text-slate-400 pb-2 border-b border-slate-800 shrink-0">
              <span className="flex items-center gap-1.5">
                <Terminal className="w-3.5 h-3.5 text-cyan-400" />
                <span>AgentCore Real-time Execution Pipeline Log</span>
              </span>
              <button
                onClick={() => setLogs([])}
                className="text-[10px] text-slate-500 hover:text-slate-300 transition cursor-pointer"
              >
                Clear Log
              </button>
            </div>

            <div className="flex-1 overflow-y-auto space-y-1.5 text-[11px] pr-2 text-slate-300">
              {logs.length === 0 ? (
                <div className="text-slate-600 italic mt-4">
                  Ready. Click &quot;Run AgentCore Loop&quot; or select a preset to execute the verified Phase 1 pipeline...
                </div>
              ) : (
                logs.map((l, idx) => (
                  <div key={idx} className="leading-tight">
                    <span className="text-slate-600 select-none">&gt; </span>
                    <span className={l.includes('COMPLETED') || l.includes('PASSED') ? 'text-emerald-400 font-bold' : l.includes('FAILED') || l.includes('BLOCKED') || l.includes('ERROR') ? 'text-rose-400 font-bold' : l.includes('WAITING') || l.includes('CONFIRM') ? 'text-amber-300 font-bold' : 'text-slate-300'}>
                      {l}
                    </span>
                  </div>
                ))
              )}
              <div ref={logsEndRef} />
            </div>
          </div>

          {/* Persisted Room Record Details */}
          <div className="p-4 rounded-xl bg-slate-900 border border-slate-800 space-y-2">
            <div className="flex items-center justify-between">
              <span className="text-xs font-bold text-white flex items-center gap-2">
                <ShieldCheck className="w-4 h-4 text-emerald-400" />
                <span>Room SQLite Persisted Record</span>
              </span>
              <button
                onClick={async () => {
                  await RoomTaskRepository.clearAll();
                  await refreshHistory();
                  setActiveTask(null);
                }}
                className="text-[10px] text-rose-400 hover:underline cursor-pointer"
              >
                Wipe Room DB
              </button>
            </div>

            {activeTask ? (
              <div className="space-y-2 text-xs">
                <div className="grid grid-cols-2 gap-2 text-[11px] font-mono bg-slate-950 p-2.5 rounded-lg border border-slate-800">
                  <div><span className="text-slate-500">TASK_ID:</span> {activeTask.id}</div>
                  <div><span className="text-slate-500">STATUS:</span> <span className="text-cyan-400 font-bold">{activeTask.status}</span></div>
                  <div><span className="text-slate-500">PROVIDER:</span> {activeTask.provider}</div>
                  <div><span className="text-slate-500">MODEL:</span> {activeTask.model}</div>
                </div>

                {activeTask.finalResponse && (
                  <div className="p-2.5 rounded-lg bg-emerald-950/30 border border-emerald-900/40 text-emerald-300 text-xs font-sans">
                    <span className="font-bold text-emerald-400">Final Verified Response: </span>
                    {activeTask.finalResponse}
                  </div>
                )}
                {activeTask.errorMessage && (
                  <div className="p-2.5 rounded-lg bg-rose-950/30 border border-rose-900/40 text-rose-300 text-xs font-sans">
                    <span className="font-bold text-rose-400">Error: </span>
                    {activeTask.errorMessage}
                  </div>
                )}
              </div>
            ) : (
              <div className="text-xs text-slate-500 italic p-3 bg-slate-950 rounded-lg border border-slate-800 text-center">
                No active task. Run an execution above to inspect durable state persistence.
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};
