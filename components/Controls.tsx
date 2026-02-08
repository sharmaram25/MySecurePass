import React from 'react';
import { PasswordOptions, PasswordType, PresetType, DEFAULT_OPTIONS } from '../types';
import { Lock, Hash, Type, Mic, Briefcase, Code, Users, ShieldCheck, Settings2, Sparkles } from 'lucide-react';
import { motion, AnimatePresence } from 'framer-motion';

interface Props {
  options: PasswordOptions;
  setOptions: (opt: PasswordOptions) => void;
}

const Controls: React.FC<Props> = ({ options, setOptions }) => {
  
  const applyPreset = (preset: PresetType) => {
    let newOptions = { ...DEFAULT_OPTIONS, preset };
    
    switch (preset) {
      case PresetType.BANKING:
        newOptions = { ...newOptions, length: 24, type: PasswordType.RANDOM, useSymbols: true, minSymbols: 2, minNumbers: 2, excludeSimilar: true, noSymbolsAtEdges: true };
        break;
      case PresetType.SOCIAL:
        newOptions = { ...newOptions, length: 12, type: PasswordType.PRONOUNCEABLE, useSymbols: true, minSymbols: 1, minNumbers: 1, useUppercase: true };
        break;
      case PresetType.DEVELOPER:
        newOptions = { ...newOptions, length: 20, type: PasswordType.RANDOM, useSymbols: true, excludeSimilar: true, noSymbolsAtEdges: false };
        break;
      case PresetType.ENTERPRISE:
        newOptions = { ...newOptions, length: 16, type: PasswordType.RANDOM, useSymbols: true, minSymbols: 3, minNumbers: 3, useUppercase: true };
        break;
      case PresetType.WIFI:
        newOptions = { ...newOptions, length: 16, type: PasswordType.RANDOM, useSymbols: false, useNumbers: true, useUppercase: true, excludeSimilar: true };
        break;
      case PresetType.READABLE:
        newOptions = { ...newOptions, length: 18, type: PasswordType.PRONOUNCEABLE, useSymbols: false, useNumbers: true, useUppercase: true };
        break;
      case PresetType.PIN_4:
        newOptions = { ...newOptions, length: 4, type: PasswordType.PIN, useSymbols: false, useNumbers: true, useUppercase: false, useLowercase: false };
        break;
      case PresetType.PIN_6:
        newOptions = { ...newOptions, length: 6, type: PasswordType.PIN, useSymbols: false, useNumbers: true, useUppercase: false, useLowercase: false };
        break;
      case PresetType.API_KEY:
        newOptions = { ...newOptions, length: 32, type: PasswordType.RANDOM, useSymbols: false, useNumbers: true, useUppercase: true, useLowercase: true, excludeSimilar: false };
        break;
      case PresetType.STREAMING:
        newOptions = { ...newOptions, length: 12, type: PasswordType.RANDOM, useSymbols: false, useNumbers: true, useUppercase: false, useLowercase: true, excludeSimilar: true };
        break;
      case PresetType.STANDARD:
        newOptions = { ...newOptions, length: 14, type: PasswordType.RANDOM, useSymbols: true, minSymbols: 1, useNumbers: true, minNumbers: 1, useUppercase: true, useLowercase: true, excludeSimilar: true };
        break;
      case PresetType.PASSPHRASE:
        newOptions = { ...newOptions, length: 4, type: PasswordType.MEMORABLE, useSymbols: false, useNumbers: false, useUppercase: true, useLowercase: true };
        break;
      case PresetType.CUSTOM:
      default:
        // Keep current custom state or reset? Let's just switch mode.
        newOptions = { ...options, preset: PresetType.CUSTOM };
        break;
    }
    setOptions(newOptions);
  };

  const handleChange = (key: keyof PasswordOptions, value: any) => {
    setOptions({ ...options, preset: PresetType.CUSTOM, [key]: value });
  };

  return (
    <div className="space-y-6">
      
      {/* Top Level Mode Selector */}
      <div className="flex bg-slate-100 dark:bg-slate-800 p-1 rounded-xl">
        <button
          onClick={() => applyPreset(PresetType.CUSTOM)}
          className={`flex-1 py-2 rounded-lg text-sm font-bold flex items-center justify-center gap-2 transition-all ${options.preset === PresetType.CUSTOM ? 'bg-white dark:bg-slate-700 shadow-sm text-brand-blue dark:text-brand-teal' : 'text-slate-400'}`}
        >
          <Settings2 size={16} /> Custom
        </button>
        <button
          onClick={() => applyPreset(PresetType.BANKING)} // Default to a preset view
          className={`flex-1 py-2 rounded-lg text-sm font-bold flex items-center justify-center gap-2 transition-all ${options.preset !== PresetType.CUSTOM ? 'bg-white dark:bg-slate-700 shadow-sm text-brand-blue dark:text-brand-teal' : 'text-slate-400'}`}
        >
          <Sparkles size={16} /> Presets
        </button>
      </div>

      <AnimatePresence mode="wait">
        {options.preset !== PresetType.CUSTOM ? (
          <motion.div 
            key="presets"
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -10 }}
            className="grid grid-cols-2 md:grid-cols-3 gap-3"
          >
            <PresetBtn 
              active={options.preset === PresetType.BANKING} 
              icon={<ShieldCheck size={20}/>} 
              title="Banking" 
              desc="Max entropy" 
              onClick={() => applyPreset(PresetType.BANKING)} 
            />
            <PresetBtn 
              active={options.preset === PresetType.SOCIAL} 
              icon={<Users size={20}/>} 
              title="Social" 
              desc="Easy to type" 
              onClick={() => applyPreset(PresetType.SOCIAL)} 
            />
            <PresetBtn 
              active={options.preset === PresetType.WIFI} 
              icon={<svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M5 12.55a11 11 0 0 1 14.08 0"/><path d="M1.42 9a16 16 0 0 1 21.16 0"/><path d="M8.53 16.11a6 6 0 0 1 6.95 0"/><line x1="12" y1="20" x2="12.01" y2="20"/></svg>} 
              title="Wi-Fi" 
              desc="No symbols" 
              onClick={() => applyPreset(PresetType.WIFI)} 
            />
            <PresetBtn 
              active={options.preset === PresetType.DEVELOPER} 
              icon={<Code size={20}/>} 
              title="Developer" 
              desc="No ambiguous" 
              onClick={() => applyPreset(PresetType.DEVELOPER)} 
            />
            <PresetBtn 
              active={options.preset === PresetType.ENTERPRISE} 
              icon={<Briefcase size={20}/>} 
              title="Work" 
              desc="Strict rules" 
              onClick={() => applyPreset(PresetType.ENTERPRISE)} 
            />
            <PresetBtn 
              active={options.preset === PresetType.READABLE} 
              icon={<Type size={20}/>} 
              title="Readable" 
              desc="Pronounceable" 
              onClick={() => applyPreset(PresetType.READABLE)} 
            />
            <PresetBtn 
              active={options.preset === PresetType.PIN_4} 
              icon={<Hash size={20}/>} 
              title="PIN (4)" 
              desc="4-Digit" 
              onClick={() => applyPreset(PresetType.PIN_4)} 
            />
            <PresetBtn 
              active={options.preset === PresetType.PIN_6} 
              icon={<Hash size={20}/>} 
              title="PIN (6)" 
              desc="6-Digit" 
              onClick={() => applyPreset(PresetType.PIN_6)} 
            />
            <PresetBtn 
              active={options.preset === PresetType.API_KEY} 
              icon={<Settings2 size={20}/>} 
              title="API Key" 
              desc="32 chars" 
              onClick={() => applyPreset(PresetType.API_KEY)} 
            />
            <PresetBtn 
              active={options.preset === PresetType.STANDARD} 
              icon={<ShieldCheck size={20}/>} 
              title="Online Acct" 
              desc="Standard" 
              onClick={() => applyPreset(PresetType.STANDARD)} 
            />
            <PresetBtn 
              active={options.preset === PresetType.STREAMING} 
              icon={<svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><rect width="20" height="15" x="2" y="7" rx="2" ry="2"/><polyline points="17 2 12 7 7 2"/></svg>} 
              title="TV / Console" 
              desc="Easy type" 
              onClick={() => applyPreset(PresetType.STREAMING)} 
            />
            <PresetBtn 
              active={options.preset === PresetType.PASSPHRASE} 
              icon={<Type size={20}/>} 
              title="Passphrase" 
              desc="4 Words" 
              onClick={() => applyPreset(PresetType.PASSPHRASE)} 
            />
          </motion.div>
        ) : (
          <motion.div 
            key="custom"
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -10 }}
            className="space-y-6"
          >
            {/* Type Selection */}
            <div className="flex p-1 bg-slate-100 dark:bg-slate-800 rounded-xl overflow-x-auto">
              {[
                { label: 'Random', type: PasswordType.RANDOM, icon: <Hash size={18} /> },
                { label: 'Phonetic', type: PasswordType.PRONOUNCEABLE, icon: <Mic size={18} /> },
                { label: 'Phrase', type: PasswordType.MEMORABLE, icon: <Type size={18} /> },
                { label: 'PIN', type: PasswordType.PIN, icon: <Lock size={18} /> },
              ].map((t) => (
                <button
                  key={t.type}
                  onClick={() => handleChange('type', t.type)}
                  className={`flex-1 min-w-[90px] flex flex-col sm:flex-row items-center justify-center gap-2 py-3 px-2 text-xs sm:text-sm font-bold rounded-lg transition-all focus:outline-none focus:ring-2 focus:ring-brand-blue/50 ${
                    options.type === t.type
                      ? 'bg-white dark:bg-slate-700 text-brand-blue dark:text-brand-teal shadow-sm ring-1 ring-black/5 dark:ring-white/10'
                      : 'text-slate-500 hover:text-slate-700 dark:text-slate-400 dark:hover:text-slate-200'
                  }`}
                >
                  {t.icon}
                  <span>{t.label}</span>
                </button>
              ))}
            </div>

            {/* Length */}
            <div className="space-y-4">
              <div className="flex justify-between items-center">
                <label className="text-xs font-bold text-slate-500 dark:text-slate-400 uppercase tracking-wider">Length</label>
                <div className="bg-brand-blue/10 dark:bg-brand-teal/20 text-brand-blue dark:text-brand-teal px-4 py-1 rounded-full font-mono font-bold text-sm">
                  {options.length}
                </div>
              </div>
              <input
                type="range"
                min="4"
                max={options.type === PasswordType.MEMORABLE ? 100 : 64}
                value={options.length}
                onChange={(e) => handleChange('length', parseInt(e.target.value))}
                className="w-full h-2 bg-slate-200 dark:bg-slate-700 rounded-lg appearance-none cursor-pointer accent-brand-blue dark:accent-brand-teal focus:outline-none focus:ring-2 focus:ring-brand-blue/50"
              />
            </div>

            {/* Toggles */}
            {(options.type === PasswordType.RANDOM || options.type === PasswordType.PRONOUNCEABLE) && (
              <div className="grid grid-cols-2 gap-3">
                <Toggle label="A-Z" checked={options.useUppercase} onChange={(c) => handleChange('useUppercase', c)} />
                <Toggle label="a-z" checked={options.useLowercase} onChange={(c) => handleChange('useLowercase', c)} />
                <Toggle label="0-9" checked={options.useNumbers} onChange={(c) => handleChange('useNumbers', c)} />
                <Toggle label="#$%" checked={options.useSymbols} onChange={(c) => handleChange('useSymbols', c)} />
              </div>
            )}

            {/* Advanced Section */}
            {(options.type === PasswordType.RANDOM) && (
              <div className="pt-4 border-t border-slate-100 dark:border-slate-800">
                <p className="text-[10px] font-bold text-slate-400 uppercase tracking-widest mb-3">Advanced Rules</p>
                <div className="space-y-3">
                  <div className="flex items-center justify-between">
                     <span className="text-xs text-slate-600 dark:text-slate-400">Min Numbers</span>
                     <div className="flex items-center gap-2">
                        <input 
                          type="range" min="0" max="8" value={options.minNumbers} 
                          onChange={(e) => handleChange('minNumbers', parseInt(e.target.value))}
                          className="w-24 h-1 bg-slate-200 dark:bg-slate-700 rounded-lg appearance-none cursor-pointer accent-brand-blue"
                        />
                        <span className="text-xs font-mono w-4">{options.minNumbers}</span>
                     </div>
                  </div>
                  <div className="flex items-center justify-between">
                     <span className="text-xs text-slate-600 dark:text-slate-400">Min Symbols</span>
                     <div className="flex items-center gap-2">
                        <input 
                          type="range" min="0" max="8" value={options.minSymbols} 
                          onChange={(e) => handleChange('minSymbols', parseInt(e.target.value))}
                          className="w-24 h-1 bg-slate-200 dark:bg-slate-700 rounded-lg appearance-none cursor-pointer accent-brand-blue"
                        />
                        <span className="text-xs font-mono w-4">{options.minSymbols}</span>
                     </div>
                  </div>
                  <Toggle label="Exclude Similar (l/1, O/0)" checked={options.excludeSimilar} onChange={(c) => handleChange('excludeSimilar', c)} fullWidth small />
                  <Toggle label="No Symbols at Start/End" checked={options.noSymbolsAtEdges} onChange={(c) => handleChange('noSymbolsAtEdges', c)} fullWidth small />
                </div>
              </div>
            )}
          </motion.div>
        )}
      </AnimatePresence>

    </div>
  );
};

const PresetBtn: React.FC<{active: boolean, icon: any, title: string, desc: string, onClick: () => void}> = ({active, icon, title, desc, onClick}) => (
  <button onClick={onClick} className={`p-4 rounded-xl border-2 text-left transition-all hover:scale-[1.02] active:scale-95 focus:outline-none focus:ring-2 focus:ring-brand-blue/50 ${active ? 'border-brand-blue bg-brand-blue/5 dark:bg-brand-blue/10 dark:border-brand-teal' : 'border-slate-100 dark:border-slate-800 bg-white dark:bg-slate-800/50 hover:border-brand-blue/30'}`}>
    <div className={`${active ? 'text-brand-blue dark:text-brand-teal' : 'text-slate-400'}`}>{icon}</div>
    <div className={`text-sm font-bold mt-2 ${active ? 'text-slate-800 dark:text-white' : 'text-slate-600 dark:text-slate-400'}`}>{title}</div>
    <div className="text-xs text-slate-500 dark:text-slate-500 leading-tight mt-1">{desc}</div>
  </button>
);

const Toggle: React.FC<{ label: string; checked: boolean; onChange: (c: boolean) => void; fullWidth?: boolean; small?: boolean }> = ({
  label,
  checked,
  onChange,
  fullWidth,
  small
}) => (
  <label className={`flex items-center justify-between cursor-pointer group rounded-lg transition-colors ${fullWidth ? 'w-full' : ''} ${small ? 'py-2' : 'p-3 bg-slate-50 dark:bg-slate-800/50 border border-transparent hover:border-slate-200 dark:hover:border-slate-700'}`}>
    <span className={`text-sm font-semibold transition-colors ${checked ? 'text-slate-800 dark:text-slate-200' : 'text-slate-500 dark:text-slate-400'}`}>
      {label}
    </span>
    <div className="relative">
      <input
        type="checkbox"
        className="sr-only"
        checked={checked}
        onChange={(e) => onChange(e.target.checked)}
      />
      <div
        className={`w-10 h-6 rounded-full transition-all duration-300 ${
          checked ? 'bg-brand-blue dark:bg-brand-teal' : 'bg-slate-200 dark:bg-slate-700'
        }`}
      />
      <div
        className={`absolute top-1 left-1 bg-white w-4 h-4 rounded-full transition-all duration-300 shadow-sm ${
          checked ? 'translate-x-4' : 'translate-x-0'
        }`}
      />
    </div>
  </label>
);

export default Controls;
