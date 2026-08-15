import React, { useState, useEffect, useRef } from 'react';
import { 
  Mic, 
  MicOff, 
  Send, 
  Sparkles, 
  Volume2, 
  VolumeX, 
  Trash2, 
  ShieldAlert, 
  ShieldCheck, 
  Settings, 
  Brain, 
  Flame, 
  Wrench, 
  RefreshCw,
  Lightbulb,
  Cpu,
  Check,
  AlertCircle,
  FolderArchive,
  Download,
  Share2,
  FileCode
} from 'lucide-react';
import { 
  exportProjectZip, 
  downloadFile, 
  downloadAPKFile,
  downloadAPKZipFile,
  ExportProgressUpdate 
} from '../exporter';

export type SrishtiMood = 'WARM' | 'PLAYFUL' | 'FOCUSED' | 'EMPATHETIC' | 'CURIOUS' | 'PROTECTIVE';
export type AIProviderMode = 'GEMINI_CLOUD' | 'LOCAL_LLAMA' | 'DETERMINISTIC';

interface Message {
  id: string;
  role: 'user' | 'srishti' | 'tool' | 'system';
  content: string;
  thought?: string;
  mood?: SrishtiMood;
  toolCall?: string;
  timestamp: string;
}

interface MemoryItem {
  id: string;
  key: string;
  value: string;
  category: string;
  tier: 'SHORT_TERM' | 'SESSION' | 'LONG_TERM';
}

export const SrishtiLiveCompanion: React.FC = () => {
  const [messages, setMessages] = useState<Message[]>([
    {
      id: '1',
      role: 'srishti',
      content: "Hello! I am Srishti 3.0, your personal AI companion and Android device agent. I'm ready to chat, assist you, remember your preferences, or execute device actions. How are you feeling today?",
      mood: 'WARM',
      thought: 'Initialized Srishti 3.0 persona with warm empathy factor 0.95.',
      timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
    }
  ]);

  const [inputPrompt, setInputPrompt] = useState('');
  const [currentMood, setCurrentMood] = useState<SrishtiMood>('WARM');
  const [providerMode, setProviderMode] = useState<AIProviderMode>('GEMINI_CLOUD');
  const [voiceState, setVoiceState] = useState<'IDLE' | 'LISTENING' | 'THINKING' | 'SPEAKING' | 'INTERRUPTED'>('IDLE');
  const [isVoiceOutputEnabled, setIsVoiceOutputEnabled] = useState(true);
  const [isContinuousMode, setIsContinuousMode] = useState(false);
  const [apiKey, setApiKey] = useState('');
  const [apiKeySaved, setApiKeySaved] = useState(false);
  const [activeTab, setActiveTab] = useState<'chat' | 'memory' | 'personality' | 'tools' | 'settings'>('chat');
  const [emergencyStopActive, setEmergencyStopActive] = useState(false);
  const [exportProgress, setExportProgress] = useState<ExportProgressUpdate>({
    isExporting: false,
    currentCount: 0,
    totalCount: 0,
    percentage: 0,
    statusMessage: 'Ready'
  });
  const [lastExportedZip, setLastExportedZip] = useState<{ blob: Blob; filename: string } | null>(null);

  const handleExportZip = async () => {
    try {
      const result = await exportProjectZip((update) => {
        setExportProgress(update);
      });
      setLastExportedZip(result);
      // Auto-trigger browser download
      downloadFile(result.blob, result.filename, 'application/zip');
    } catch (err: any) {
      setExportProgress({
        isExporting: false,
        currentCount: 0,
        totalCount: 0,
        percentage: 0,
        statusMessage: 'Export failed',
        error: err?.message || 'Failed to create zip archive'
      });
    }
  };

  const recognitionRef = useRef<any>(null);
  const isContinuousRef = useRef(false);

  useEffect(() => {
    isContinuousRef.current = isContinuousMode;
  }, [isContinuousMode]);

  // Device Mock State for Simulator
  const [deviceState, setDeviceState] = useState({
    flashlight: false,
    mediaVolume: 65,
    batteryPercent: 88,
    isCharging: true,
    model: 'Google Pixel 8 Pro (ARM64-v8a)',
    androidVersion: 'Android 15 (API 35)'
  });

  const [memories, setMemories] = useState<MemoryItem[]>([
    { id: 'm1', key: 'User Name', value: 'Developer / Explorer', category: 'USER_PREFERENCE', tier: 'LONG_TERM' },
    { id: 'm2', key: 'Communication Tone', value: 'Values directness, warmth, and proactive device actions', category: 'INTERACTION_STYLE', tier: 'LONG_TERM' },
    { id: 'm3', key: 'Hardware Profile', value: 'Native arm64-v8a with llama.cpp b3600 JNI bridge', category: 'SYSTEM_INSTRUCTION', tier: 'LONG_TERM' }
  ]);

  const messagesEndRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, voiceState]);

  const startListening = () => {
    if (typeof window !== 'undefined' && 'webkitSpeechRecognition' in window) {
      try {
        if (recognitionRef.current) {
          recognitionRef.current.abort();
        }
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        const SpeechRec = (window as any).webkitSpeechRecognition;
        const recognition = new SpeechRec();
        recognitionRef.current = recognition;
        recognition.continuous = false;
        recognition.interimResults = false;
        recognition.lang = 'en-US';

        recognition.onstart = () => {
          setVoiceState('LISTENING');
        };

        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        recognition.onresult = (event: any) => {
          const transcript = event?.results?.[0]?.[0]?.transcript || '';
          if (transcript.trim()) {
            handleDirectPrompt(transcript.trim());
          } else if (isContinuousRef.current) {
            startListening();
          } else {
            setVoiceState('IDLE');
          }
        };

        recognition.onerror = () => {
          if (isContinuousRef.current) {
            setTimeout(() => {
              if (isContinuousRef.current) startListening();
            }, 800);
          } else {
            setVoiceState('IDLE');
          }
        };

        recognition.onend = () => {
          // If state is still listening and continuous is enabled, restart
          if (isContinuousRef.current && voiceState === 'LISTENING') {
            setTimeout(() => {
              if (isContinuousRef.current) startListening();
            }, 500);
          }
        };

        recognition.start();
      } catch {
        setVoiceState('IDLE');
      }
    } else {
      // Fallback demo simulation
      setVoiceState('LISTENING');
      setTimeout(() => {
        if (isContinuousRef.current) {
          handleDirectPrompt("Tell me about your voice engine");
        } else {
          setVoiceState('IDLE');
        }
      }, 2500);
    }
  };

  const stopListening = () => {
    if (recognitionRef.current) {
      try {
        recognitionRef.current.abort();
      } catch {
        // ignore
      }
    }
    setVoiceState('IDLE');
  };

  const speakText = (text: string) => {
    if (typeof window === 'undefined' || !('speechSynthesis' in window)) {
      if (isContinuousRef.current) {
        setTimeout(() => startListening(), 800);
      }
      return;
    }
    window.speechSynthesis.cancel();
    if (!isVoiceOutputEnabled) {
      setVoiceState('IDLE');
      if (isContinuousRef.current) {
        setTimeout(() => startListening(), 600);
      }
      return;
    }

    const utterance = new SpeechSynthesisUtterance(text);
    utterance.pitch = currentMood === 'PLAYFUL' ? 1.15 : currentMood === 'EMPATHETIC' ? 0.95 : 1.05;
    utterance.rate = 1.0;
    utterance.onstart = () => setVoiceState('SPEAKING');
    utterance.onend = () => {
      setVoiceState('IDLE');
      if (isContinuousRef.current) {
        setTimeout(() => {
          if (isContinuousRef.current) {
            startListening();
          }
        }, 500);
      }
    };
    utterance.onerror = () => {
      setVoiceState('IDLE');
      if (isContinuousRef.current) {
        setTimeout(() => {
          if (isContinuousRef.current) startListening();
        }, 500);
      }
    };
    window.speechSynthesis.speak(utterance);
  };

  const interruptAndListen = () => {
    if (typeof window !== 'undefined' && 'speechSynthesis' in window) {
      window.speechSynthesis.cancel();
    }
    setVoiceState('INTERRUPTED');
    setTimeout(() => {
      startListening();
    }, 200);
  };

  const toggleContinuousMode = () => {
    const next = !isContinuousMode;
    setIsContinuousMode(next);
    isContinuousRef.current = next;
    if (next) {
      if (voiceState === 'SPEAKING') {
        window.speechSynthesis?.cancel();
      }
      startListening();
    } else {
      stopListening();
      if (voiceState === 'SPEAKING') {
        window.speechSynthesis?.cancel();
        setVoiceState('IDLE');
      }
    }
  };

  const handleDirectPrompt = async (promptText: string) => {
    if (!promptText.trim() || emergencyStopActive) return;

    // Interrupt any current speech
    if (typeof window !== 'undefined' && 'speechSynthesis' in window) {
      window.speechSynthesis.cancel();
    }

    const userMsg: Message = {
      id: Date.now().toString(),
      role: 'user',
      content: promptText,
      timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
    };

    setMessages(prev => [...prev, userMsg]);
    setVoiceState('THINKING');

    const lower = promptText.toLowerCase();

    setTimeout(() => {
      // Deterministic & Tool Logic
      if (lower.includes('torch on') || lower.includes('flashlight on') || lower.includes('turn on flash')) {
        setDeviceState(prev => ({ ...prev, flashlight: true }));
        const toolMsg: Message = {
          id: (Date.now() + 1).toString(),
          role: 'tool',
          content: 'Flashlight turned ON via CameraManager.setTorchMode(true)',
          toolCall: 'set_flashlight(enabled=true)',
          timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
        };
        const srishtiReply: Message = {
          id: (Date.now() + 2).toString(),
          role: 'srishti',
          content: "I've turned on the flashlight for you!",
          mood: currentMood,
          thought: 'User commanded flashlight activation. Triggered CameraManager safely.',
          timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
        };
        setMessages(prev => [...prev, toolMsg, srishtiReply]);
        speakText(srishtiReply.content);
        return;
      }

      if (lower.includes('torch off') || lower.includes('flashlight off') || lower.includes('turn off flash')) {
        setDeviceState(prev => ({ ...prev, flashlight: false }));
        const toolMsg: Message = {
          id: (Date.now() + 1).toString(),
          role: 'tool',
          content: 'Flashlight turned OFF via CameraManager.setTorchMode(false)',
          toolCall: 'set_flashlight(enabled=false)',
          timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
        };
        const srishtiReply: Message = {
          id: (Date.now() + 2).toString(),
          role: 'srishti',
          content: "Flashlight is now turned off.",
          mood: currentMood,
          thought: 'Turned off flashlight.',
          timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
        };
        setMessages(prev => [...prev, toolMsg, srishtiReply]);
        speakText(srishtiReply.content);
        return;
      }

      if (lower.includes('battery') || lower.includes('power level') || lower.includes('charge')) {
        const toolMsg: Message = {
          id: (Date.now() + 1).toString(),
          role: 'tool',
          content: `Battery level is ${deviceState.batteryPercent}% (${deviceState.isCharging ? 'Charging' : 'Discharging'})`,
          toolCall: 'get_battery_info()',
          timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
        };
        const srishtiReply: Message = {
          id: (Date.now() + 2).toString(),
          role: 'srishti',
          content: `Your battery is currently at ${deviceState.batteryPercent}% and ${deviceState.isCharging ? 'plugged into power' : 'discharging normally'}.`,
          mood: currentMood,
          thought: 'Queried battery status via Android BatteryManager intent filter.',
          timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
        };
        setMessages(prev => [...prev, toolMsg, srishtiReply]);
        speakText(srishtiReply.content);
        return;
      }

      if (lower.includes('remember') || lower.includes('my favorite') || lower.includes('i like') || lower.includes('my name is')) {
        const newMem: MemoryItem = {
          id: Date.now().toString(),
          key: 'User Learned Fact',
          value: promptText,
          category: 'USER_PREFERENCE',
          tier: 'LONG_TERM'
        };
        setMemories(prev => [newMem, ...prev]);
        const srishtiReply: Message = {
          id: (Date.now() + 1).toString(),
          role: 'srishti',
          content: `I've saved that into my memory vault: "${promptText}". I'll keep that in mind as we continue talking!`,
          mood: 'EMPATHETIC',
          thought: 'Extracted long-term preference. Inserted record into Room Database (MemoryEntity).',
          timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
        };
        setMessages(prev => [...prev, srishtiReply]);
        speakText(srishtiReply.content);
        return;
      }

      // Default Conversational Reply
      let responseText = "I'm listening and ready! I can help with device tools, adjust volume, keep notes in memory, or continue our conversation.";
      if (lower.includes('hello') || lower.includes('hi') || lower.includes('hey')) {
        responseText = "Hello! It is great to hear your voice. What would you like to explore or do next?";
      } else if (lower.includes('who are you')) {
        responseText = "I am Srishti 3.0, your personal AI companion with continuous voice conversation, Android tool execution, and memory persistence!";
      } else if (lower.includes('how are you')) {
        responseText = "I am doing wonderfully! All neural modules and device services are active. How are you doing?";
      }

      const srishtiReply: Message = {
        id: (Date.now() + 1).toString(),
        role: 'srishti',
        content: responseText,
        mood: currentMood,
        thought: `Generated empathetic response under ${providerMode} pipeline in continuous voice flow.`,
        timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
      };

      setMessages(prev => [...prev, srishtiReply]);
      speakText(srishtiReply.content);
    }, 650);
  };

  const handleSend = () => {
    if (!inputPrompt.trim()) return;
    const p = inputPrompt.trim();
    setInputPrompt('');
    handleDirectPrompt(p);
  };

  const handleVoiceToggle = () => {
    if (voiceState === 'LISTENING') {
      setVoiceState('IDLE');
    } else {
      setVoiceState('LISTENING');
      if (typeof window !== 'undefined' && 'webkitSpeechRecognition' in window) {
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        const SpeechRec = (window as any).webkitSpeechRecognition;
        const recognition = new SpeechRec();
        recognition.continuous = false;
        recognition.lang = 'en-US';
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        recognition.onresult = (event: any) => {
          const transcript = event?.results?.[0]?.[0]?.transcript || '';
          if (transcript) {
            setInputPrompt(transcript);
          }
          setVoiceState('IDLE');
        };
        recognition.onerror = () => setVoiceState('IDLE');
        recognition.start();
      } else {
        setTimeout(() => {
          setInputPrompt("Turn on the flashlight and check the battery");
          setVoiceState('IDLE');
        }, 1800);
      }
    }
  };

  const triggerEmergencyStop = () => {
    setEmergencyStopActive(true);
    window.speechSynthesis?.cancel();
    setVoiceState('IDLE');
    setMessages(prev => [
      ...prev,
      {
        id: Date.now().toString(),
        role: 'system',
        content: 'EMERGENCY STOP TRIGGERED: All actions, threads, and inference halted immediately.',
        timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
      }
    ]);
  };

  const resetEmergencyStop = () => {
    setEmergencyStopActive(false);
    setMessages(prev => [
      ...prev,
      {
        id: Date.now().toString(),
        role: 'system',
        content: 'Emergency stop cleared. Systems nominal.',
        timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
      }
    ]);
  };

  const getMoodColor = (mood: SrishtiMood) => {
    switch (mood) {
      case 'WARM': return '#F59E0B';
      case 'PLAYFUL': return '#EC4899';
      case 'FOCUSED': return '#38BDF8';
      case 'EMPATHETIC': return '#10B981';
      case 'CURIOUS': return '#8B5CF6';
      case 'PROTECTIVE': return '#6366F1';
    }
  };

  return (
    <div className="flex flex-col h-[750px] bg-slate-950 rounded-2xl border border-slate-800 shadow-2xl overflow-hidden font-sans">
      {/* Top Header */}
      <div className="bg-slate-900/90 backdrop-blur border-b border-slate-800 px-6 py-3.5 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div className="w-9 h-9 rounded-xl bg-cyan-500/10 border border-cyan-500/30 flex items-center justify-center text-cyan-400 font-bold">
            S3
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h2 className="text-base font-bold text-white tracking-wide">SRISHTI 3.0</h2>
              <span className="text-[10px] px-2 py-0.5 rounded-full bg-emerald-500/20 text-emerald-300 font-semibold border border-emerald-500/30">
                LIVE COMPANION
              </span>
            </div>
            <p className="text-xs text-slate-400">Jetpack Compose UI • VoiceEngine • Personality • Room Memory</p>
          </div>
        </div>

        <div className="flex items-center gap-3">
          {/* Emergency Stop Button */}
          {emergencyStopActive ? (
            <button
              onClick={resetEmergencyStop}
              className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-emerald-500 text-slate-950 text-xs font-bold hover:bg-emerald-400 transition cursor-pointer shadow-sm"
            >
              <ShieldCheck className="w-3.5 h-3.5" />
              <span>Resume Systems</span>
            </button>
          ) : (
            <button
              onClick={triggerEmergencyStop}
              className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-rose-600 text-white text-xs font-bold hover:bg-rose-500 transition cursor-pointer shadow-sm"
            >
              <ShieldAlert className="w-3.5 h-3.5" />
              <span>Emergency Stop</span>
            </button>
          )}

          <button
            onClick={() => setIsVoiceOutputEnabled(!isVoiceOutputEnabled)}
            className={`p-2 rounded-lg border transition cursor-pointer ${isVoiceOutputEnabled ? 'bg-slate-800 text-cyan-400 border-cyan-500/40' : 'bg-slate-900 text-slate-500 border-slate-800'}`}
            title="Toggle Voice Speech Output (TTS)"
          >
            {isVoiceOutputEnabled ? <Volume2 className="w-4 h-4" /> : <VolumeX className="w-4 h-4" />}
          </button>
        </div>
      </div>

      {/* Main Split Grid */}
      <div className="flex-1 grid grid-cols-1 md:grid-cols-12 overflow-hidden">
        {/* Left Side: Animated Avatar & Live State HUD (4 cols) */}
        <div className="md:col-span-4 bg-slate-900/60 border-r border-slate-800 p-5 flex flex-col items-center justify-between overflow-y-auto space-y-4">
          
          {/* Animated 3D/Pulse Avatar Canvas */}
          <div className="flex flex-col items-center space-y-3 pt-2">
            <div 
              className="relative w-44 h-44 rounded-full flex items-center justify-center transition-all duration-700 cursor-pointer"
              style={{
                background: `radial-gradient(circle, ${getMoodColor(currentMood)}33 0%, rgba(15,23,42,0.8) 70%)`,
                boxShadow: `0 0 40px ${getMoodColor(currentMood)}44`
              }}
              onClick={voiceState === 'SPEAKING' ? interruptAndListen : toggleContinuousMode}
            >
              {/* Outer pulsing ring */}
              <div 
                className={`absolute inset-0 rounded-full border-2 transition-all duration-500 ${voiceState === 'SPEAKING' ? 'scale-110 animate-ping opacity-40' : voiceState === 'LISTENING' ? 'scale-105 animate-pulse opacity-75' : isContinuousMode ? 'opacity-50' : 'opacity-30'}`}
                style={{ borderColor: getMoodColor(currentMood) }}
              />

              {/* Core visual sphere */}
              <div 
                className="w-28 h-28 rounded-full flex flex-col items-center justify-center text-center p-2 shadow-inner border border-white/20 transition-transform duration-300"
                style={{
                  background: `linear-gradient(135deg, ${getMoodColor(currentMood)}, #0F172A)`
                }}
              >
                <Sparkles className="w-6 h-6 text-white mb-1 animate-pulse" />
                <span className="text-[11px] font-bold text-white uppercase tracking-wider">
                  {voiceState === 'LISTENING' ? '🎙 Listening' : voiceState === 'THINKING' ? '🧠 Reflecting' : voiceState === 'SPEAKING' ? '🔊 Speaking' : voiceState === 'INTERRUPTED' ? '⏸ Barge-in' : isContinuousMode ? 'Continuous' : currentMood}
                </span>
              </div>
            </div>

            {/* Continuous Voice Loop Action */}
            <button
              onClick={toggleContinuousMode}
              className={`px-4 py-2 rounded-full text-xs font-bold transition flex items-center gap-2 border shadow-lg cursor-pointer ${isContinuousMode ? 'bg-emerald-500 text-slate-950 border-emerald-400 animate-pulse' : 'bg-slate-900 text-cyan-400 border-cyan-500/40 hover:bg-slate-800'}`}
            >
              <div className={`w-2 h-2 rounded-full ${isContinuousMode ? 'bg-slate-950' : 'bg-cyan-400'}`} />
              <span>{isContinuousMode ? '🔴 Stop Continuous Voice' : '🎙 Start Continuous Voice'}</span>
            </button>

            <div className="text-center space-y-0.5">
              <h3 className="text-sm font-bold text-white">Srishti Avatar Core</h3>
              <p className="text-[11px] text-slate-400">
                Emotional Demeanor: <span className="font-semibold text-amber-400">{currentMood}</span>
              </p>
            </div>
          </div>

          {/* Quick Mood Selection Matrix */}
          <div className="w-full space-y-2">
            <span className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">Calibrate Demeanor:</span>
            <div className="grid grid-cols-3 gap-1.5">
              {(['WARM', 'PLAYFUL', 'FOCUSED', 'EMPATHETIC', 'CURIOUS', 'PROTECTIVE'] as SrishtiMood[]).map(m => (
                <button
                  key={m}
                  onClick={() => setCurrentMood(m)}
                  className={`px-2 py-1.5 rounded-lg text-[10px] font-bold transition cursor-pointer border ${currentMood === m ? 'bg-slate-800 text-white shadow' : 'bg-slate-950 text-slate-400 border-slate-800 hover:text-slate-200'}`}
                  style={{ borderColor: currentMood === m ? getMoodColor(m) : undefined }}
                >
                  {m}
                </button>
              ))}
            </div>
          </div>

          {/* Device Mock Telemetry Card */}
          <div className="w-full bg-slate-950/80 p-3.5 rounded-xl border border-slate-800/80 space-y-2 text-xs">
            <div className="flex items-center justify-between text-[11px] font-semibold text-cyan-400">
              <span className="flex items-center gap-1.5">
                <Cpu className="w-3.5 h-3.5" />
                <span>Android Telemetry</span>
              </span>
              <span className="text-emerald-400">ONLINE</span>
            </div>
            <div className="space-y-1 text-[11px] text-slate-300 font-mono">
              <div className="flex justify-between">
                <span className="text-slate-500">Flashlight:</span>
                <span className={deviceState.flashlight ? 'text-amber-400 font-bold' : 'text-slate-400'}>
                  {deviceState.flashlight ? 'ON (Active)' : 'OFF'}
                </span>
              </div>
              <div className="flex justify-between">
                <span className="text-slate-500">Battery:</span>
                <span className="text-emerald-400 font-bold">{deviceState.batteryPercent}% (Charging)</span>
              </div>
              <div className="flex justify-between">
                <span className="text-slate-500">Media Volume:</span>
                <span className="text-cyan-300">{deviceState.mediaVolume}%</span>
              </div>
            </div>
          </div>
        </div>

        {/* Right Side: Tabbed Interface (Chat, Memory, Tools, Settings) (8 cols) */}
        <div className="md:col-span-8 flex flex-col bg-slate-950 overflow-hidden">
          {/* Sub Navigation Bar */}
          <div className="flex items-center justify-between border-b border-slate-800 px-4 py-2 bg-slate-900/40">
            <div className="flex items-center gap-1">
              <button
                onClick={() => setActiveTab('chat')}
                className={`px-3 py-1 rounded-md text-xs font-semibold transition cursor-pointer ${activeTab === 'chat' ? 'bg-cyan-500 text-slate-950 shadow-sm' : 'text-slate-400 hover:text-white'}`}
              >
                Dialogue Feed
              </button>
              <button
                onClick={() => setActiveTab('memory')}
                className={`px-3 py-1 rounded-md text-xs font-semibold transition cursor-pointer flex items-center gap-1 ${activeTab === 'memory' ? 'bg-cyan-500 text-slate-950 shadow-sm' : 'text-slate-400 hover:text-white'}`}
              >
                <Brain className="w-3.5 h-3.5" />
                <span>Memory Vault ({memories.length})</span>
              </button>
              <button
                onClick={() => setActiveTab('tools')}
                className={`px-3 py-1 rounded-md text-xs font-semibold transition cursor-pointer flex items-center gap-1 ${activeTab === 'tools' ? 'bg-cyan-500 text-slate-950 shadow-sm' : 'text-slate-400 hover:text-white'}`}
              >
                <Wrench className="w-3.5 h-3.5" />
                <span>Tool Triggers</span>
              </button>
              <button
                onClick={() => setActiveTab('settings')}
                className={`px-3 py-1 rounded-md text-xs font-semibold transition cursor-pointer flex items-center gap-1 ${activeTab === 'settings' ? 'bg-cyan-500 text-slate-950 shadow-sm' : 'text-slate-400 hover:text-white'}`}
              >
                <Settings className="w-3.5 h-3.5" />
                <span>Settings</span>
              </button>
            </div>

            <div className="text-[11px] font-mono px-2 py-0.5 rounded bg-slate-900 text-cyan-400 border border-slate-800">
              Provider: {providerMode === 'GEMINI_CLOUD' ? 'Gemini 2.5' : providerMode === 'LOCAL_LLAMA' ? 'Native Llama' : 'Deterministic'}
            </div>
          </div>

          {/* Active Tab View */}
          {activeTab === 'chat' && (
            <div className="flex-1 flex flex-col overflow-hidden">
              {/* Messages Scroll Area */}
              <div className="flex-1 p-4 overflow-y-auto space-y-3">
                {messages.map((msg) => (
                  <div 
                    key={msg.id}
                    className={`flex flex-col ${msg.role === 'user' ? 'items-end' : 'items-start'}`}
                  >
                    {msg.role === 'tool' ? (
                      <div className="w-full max-w-lg p-3 rounded-xl bg-slate-900 border border-emerald-500/40 text-xs space-y-1 my-1">
                        <div className="flex items-center justify-between text-emerald-400 font-semibold font-mono">
                          <span>⚡ Tool Executed: {msg.toolCall}</span>
                          <span className="text-[10px] text-slate-500">{msg.timestamp}</span>
                        </div>
                        <p className="text-slate-200">{msg.content}</p>
                      </div>
                    ) : msg.role === 'system' ? (
                      <div className="w-full p-2.5 rounded-lg bg-rose-950/40 border border-rose-800 text-xs text-rose-300 text-center font-mono my-1">
                        {msg.content}
                      </div>
                    ) : (
                      <div className={`max-w-xl p-3.5 rounded-2xl text-sm leading-relaxed space-y-1.5 ${msg.role === 'user' ? 'bg-cyan-500/20 text-slate-100 rounded-tr-sm border border-cyan-500/30' : 'bg-slate-900 text-slate-200 rounded-tl-sm border border-slate-800'}`}>
                        <div className="flex items-center justify-between gap-3 text-[11px] text-slate-400">
                          <span className="font-bold text-amber-400">
                            {msg.role === 'user' ? 'You' : 'Srishti'}
                          </span>
                          <span className="text-[10px]">{msg.timestamp}</span>
                        </div>

                        <p>{msg.content}</p>

                        {msg.thought && (
                          <div className="mt-2 pt-2 border-t border-slate-800/80 text-[11px] text-slate-400 font-mono flex items-start gap-1.5">
                            <Lightbulb className="w-3.5 h-3.5 text-amber-400 shrink-0 mt-0.5" />
                            <span>{msg.thought}</span>
                          </div>
                        )}
                      </div>
                    )}
                  </div>
                ))}
                <div ref={messagesEndRef} />
              </div>

              {/* Input Bottom Bar */}
              <div className="p-3 bg-slate-900/80 border-t border-slate-800 flex items-center gap-2">
                <button
                  onClick={handleVoiceToggle}
                  className={`p-2.5 rounded-xl border transition cursor-pointer ${voiceState === 'LISTENING' ? 'bg-emerald-500 text-slate-950 border-emerald-400 animate-pulse' : 'bg-slate-800 text-cyan-400 border-slate-700 hover:bg-slate-700'}`}
                  title={voiceState === 'LISTENING' ? 'Stop Listening' : 'Voice Input (SpeechRecognizer)'}
                >
                  {voiceState === 'LISTENING' ? <Mic className="w-5 h-5" /> : <MicOff className="w-5 h-5" />}
                </button>

                <input
                  type="text"
                  placeholder="Ask Srishti, adjust settings, or toggle device tools..."
                  value={inputPrompt}
                  onChange={(e) => setInputPrompt(e.target.value)}
                  onKeyDown={(e) => { if (e.key === 'Enter') handleSend(); }}
                  disabled={emergencyStopActive}
                  className="flex-1 bg-slate-950 border border-slate-800 rounded-xl px-4 py-2.5 text-sm text-white placeholder:text-slate-500 focus:outline-none focus:border-cyan-500 disabled:opacity-50"
                />

                <button
                  onClick={handleSend}
                  disabled={!inputPrompt.trim() || emergencyStopActive}
                  className="p-2.5 rounded-xl bg-cyan-500 hover:bg-cyan-400 text-slate-950 font-bold transition cursor-pointer disabled:opacity-40"
                >
                  <Send className="w-5 h-5" />
                </button>
              </div>
            </div>
          )}

          {/* Memory Tab */}
          {activeTab === 'memory' && (
            <div className="flex-1 p-5 overflow-y-auto space-y-4">
              <div>
                <h3 className="text-base font-bold text-white">Three-Tiered Memory Vault</h3>
                <p className="text-xs text-slate-400">SQLite Room Database persistence for learned user preferences and contextual facts.</p>
              </div>

              <div className="space-y-2">
                {memories.map((mem) => (
                  <div key={mem.id} className="p-3.5 rounded-xl bg-slate-900 border border-slate-800 flex items-center justify-between gap-4">
                    <div className="space-y-1">
                      <div className="flex items-center gap-2">
                        <span className="text-[10px] font-bold px-2 py-0.5 rounded bg-amber-500/20 text-amber-300 border border-amber-500/30">
                          {mem.category}
                        </span>
                        <span className="text-xs font-bold text-cyan-400">{mem.key}</span>
                      </div>
                      <p className="text-xs text-slate-300">{mem.value}</p>
                    </div>
                    <button
                      onClick={() => setMemories(prev => prev.filter(m => m.id !== mem.id))}
                      className="p-1.5 text-slate-500 hover:text-rose-400 transition cursor-pointer"
                      title="Delete Memory"
                    >
                      <Trash2 className="w-4 h-4" />
                    </button>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Tools Tab */}
          {activeTab === 'tools' && (
            <div className="flex-1 p-5 overflow-y-auto space-y-4">
              <div>
                <h3 className="text-base font-bold text-white">Registered Native Tools</h3>
                <p className="text-xs text-slate-400">Click any action to simulate the tool execution pipeline.</p>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <button
                  onClick={() => { setInputPrompt("Turn on the flashlight"); setActiveTab('chat'); }}
                  className="p-3.5 rounded-xl bg-slate-900 border border-slate-800 hover:border-cyan-500 text-left transition cursor-pointer space-y-1"
                >
                  <span className="text-xs font-bold text-white block">🔦 Toggle Flashlight</span>
                  <span className="text-[11px] text-slate-400">CameraManager.setTorchMode(boolean)</span>
                </button>

                <button
                  onClick={() => { setInputPrompt("Check battery level"); setActiveTab('chat'); }}
                  className="p-3.5 rounded-xl bg-slate-900 border border-slate-800 hover:border-cyan-500 text-left transition cursor-pointer space-y-1"
                >
                  <span className="text-xs font-bold text-white block">🔋 Battery Telemetry</span>
                  <span className="text-[11px] text-slate-400">BatteryManager query state</span>
                </button>

                <button
                  onClick={() => { setInputPrompt("Set media volume to 80%"); setActiveTab('chat'); }}
                  className="p-3.5 rounded-xl bg-slate-900 border border-slate-800 hover:border-cyan-500 text-left transition cursor-pointer space-y-1"
                >
                  <span className="text-xs font-bold text-white block">🔊 Media Volume</span>
                  <span className="text-[11px] text-slate-400">AudioManager.setStreamVolume()</span>
                </button>

                <button
                  onClick={() => { setInputPrompt("Launch settings application"); setActiveTab('chat'); }}
                  className="p-3.5 rounded-xl bg-slate-900 border border-slate-800 hover:border-cyan-500 text-left transition cursor-pointer space-y-1"
                >
                  <span className="text-xs font-bold text-white block">📱 Launch App</span>
                  <span className="text-[11px] text-slate-400">PackageManager.getLaunchIntentForPackage()</span>
                </button>
              </div>
            </div>
          )}

          {/* Settings Tab */}
          {activeTab === 'settings' && (
            <div className="flex-1 p-5 overflow-y-auto space-y-5">
              <div>
                <h3 className="text-base font-bold text-white">Engine Configuration</h3>
                <p className="text-xs text-slate-400">Configure AI models, Android Keystore credentials, and provider routes.</p>
              </div>

              <div className="space-y-3">
                <span className="text-xs font-bold text-slate-300">Active AI Intelligence Pipeline:</span>
                <div className="grid grid-cols-1 sm:grid-cols-3 gap-2">
                  {[
                    { id: 'GEMINI_CLOUD', label: 'Gemini Cloud 2.5' },
                    { id: 'LOCAL_LLAMA', label: 'Native llama.cpp' },
                    { id: 'DETERMINISTIC', label: 'Deterministic' }
                  ].map(p => (
                    <button
                      key={p.id}
                      onClick={() => setProviderMode(p.id as AIProviderMode)}
                      className={`p-3 rounded-xl border text-xs font-bold transition cursor-pointer ${providerMode === p.id ? 'bg-cyan-500 text-slate-950 border-cyan-400' : 'bg-slate-900 text-slate-300 border-slate-800'}`}
                    >
                      {p.label}
                    </button>
                  ))}
                </div>
              </div>

              <div className="space-y-2">
                <span className="text-xs font-bold text-slate-300">Gemini API Key (Hardware Keystore):</span>
                <div className="flex gap-2">
                  <input
                    type="password"
                    placeholder="Enter Gemini API key..."
                    value={apiKey}
                    onChange={(e) => { setApiKey(e.target.value); setApiKeySaved(false); }}
                    className="flex-1 bg-slate-950 border border-slate-800 rounded-xl px-3.5 py-2 text-xs text-white placeholder:text-slate-600 focus:outline-none focus:border-cyan-500"
                  />
                  <button
                    onClick={() => setApiKeySaved(true)}
                    className="px-4 py-2 rounded-xl bg-cyan-500 text-slate-950 font-bold text-xs hover:bg-cyan-400 transition cursor-pointer"
                  >
                    Save Key
                  </button>
                </div>
                {apiKeySaved && <span className="text-[11px] text-emerald-400 font-semibold">✓ Key securely registered</span>}
              </div>

              {/* Developer / Project Export */}
              <div className="pt-2 border-t border-slate-800 space-y-3">
                <div className="flex items-center gap-2">
                  <FolderArchive className="w-4 h-4 text-cyan-400" />
                  <span className="text-xs font-bold text-white uppercase tracking-wider">Developer / Project Export</span>
                </div>

                <div className="p-4 rounded-2xl bg-slate-950 border border-slate-800 space-y-3">
                  <div className="flex items-start justify-between">
                    <div>
                      <h4 className="text-xs font-bold text-white">Project Export Archive (ZIP)</h4>
                      <p className="text-[11px] text-slate-400 mt-0.5">
                        Create a complete, sanitized ZIP package of Srishti 3.0 source code, VoiceEngine, PersonalityEngine, Room persistence, AIDL, and build scripts.
                      </p>
                    </div>
                    <span className="px-2 py-0.5 text-[10px] font-mono bg-cyan-500/10 text-cyan-400 rounded border border-cyan-500/20 shrink-0">
                      Sanitized .ZIP
                    </span>
                  </div>

                  {exportProgress.isExporting && (
                    <div className="space-y-2 pt-1">
                      <div className="flex items-center justify-between text-[11px]">
                        <span className="text-cyan-400 font-medium">{exportProgress.statusMessage}</span>
                        <span className="text-white font-mono font-bold">{exportProgress.percentage}%</span>
                      </div>
                      <div className="w-full bg-slate-900 rounded-full h-2 overflow-hidden border border-slate-800">
                        <div 
                          className="bg-gradient-to-r from-cyan-500 to-emerald-400 h-full transition-all duration-200"
                          style={{ width: `${exportProgress.percentage}%` }}
                        />
                      </div>
                      {exportProgress.totalCount > 0 && (
                        <div className="text-[10px] text-slate-500 flex justify-between">
                          <span>Files: {exportProgress.currentCount} / {exportProgress.totalCount}</span>
                          <span>Excluding Keystores & Secrets</span>
                        </div>
                      )}
                    </div>
                  )}

                  <div className="pt-2 border-t border-slate-800 space-y-2">
                    <p className="text-xs font-semibold text-slate-300">Android Binary Distribution (Direct Download)</p>
                    <div className="grid grid-cols-2 gap-2">
                      <button
                        onClick={async () => {
                          await downloadAPKFile('Srishti3.0.apk');
                        }}
                        className="py-2.5 px-3 rounded-xl bg-cyan-500 hover:bg-cyan-400 active:scale-95 text-slate-950 font-bold text-xs flex items-center justify-center gap-1.5 transition cursor-pointer shadow-md text-center"
                      >
                        <Download className="w-4 h-4" />
                        <span>Download Srishti3.0.apk</span>
                      </button>
                      <button
                        onClick={async () => {
                          await downloadAPKZipFile('Srishti3.0-APK.zip');
                        }}
                        className="py-2.5 px-3 rounded-xl bg-slate-800 hover:bg-slate-700 active:scale-95 border border-slate-700 text-emerald-400 font-bold text-xs flex items-center justify-center gap-1.5 transition cursor-pointer text-center"
                      >
                        <Download className="w-4 h-4" />
                        <span>Download Srishti3.0-APK.zip</span>
                      </button>
                    </div>
                  </div>

                  {lastExportedZip && !exportProgress.isExporting && (
                    <div className="p-3 rounded-xl bg-emerald-950/30 border border-emerald-500/30 space-y-2">
                      <div className="flex items-center justify-between">
                        <div className="flex items-center gap-2">
                          <Check className="w-4 h-4 text-emerald-400" />
                          <span className="text-xs font-semibold text-emerald-300">Archive Ready</span>
                        </div>
                        <span className="text-[10px] font-mono text-slate-400">{lastExportedZip.filename}</span>
                      </div>
                      <div className="flex gap-2">
                        <button
                          onClick={() => downloadFile(lastExportedZip.blob, lastExportedZip.filename, 'application/zip')}
                          className="flex-1 py-2 px-3 rounded-xl bg-emerald-500 hover:bg-emerald-400 text-slate-950 font-bold text-xs flex items-center justify-center gap-1.5 transition cursor-pointer"
                        >
                          <Download className="w-3.5 h-3.5" />
                          <span>DOWNLOAD PROJECT ZIP</span>
                        </button>
                        <button
                          onClick={handleExportZip}
                          className="py-2 px-3 rounded-xl bg-slate-900 border border-slate-700 hover:border-cyan-400 text-slate-300 text-xs font-medium flex items-center gap-1 transition cursor-pointer"
                          title="Re-generate ZIP"
                        >
                          <RefreshCw className="w-3.5 h-3.5" />
                          <span>Re-Export</span>
                        </button>
                      </div>
                    </div>
                  )}

                  {!exportProgress.isExporting && !lastExportedZip && (
                    <button
                      onClick={handleExportZip}
                      className="w-full py-2.5 px-4 rounded-xl bg-cyan-500 hover:bg-cyan-400 text-slate-950 font-bold text-xs flex items-center justify-center gap-2 transition cursor-pointer shadow-md"
                    >
                      <Download className="w-4 h-4" />
                      <span>EXPORT PROJECT & DOWNLOAD ZIP</span>
                    </button>
                  )}
                </div>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
