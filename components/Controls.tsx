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
            className="grid grid-cols-2 gap-3"
          >
            <PresetBtn 
              active={options.preset === PresetType.BANKING} 
              icon={<ShieldCheck size={20}/>} 
              title="Banking" 
              desc="Max entropy, random" 
              onClick={() => applyPreset(PresetType.BANKING)} 
            />
            <PresetBtn 
              active={options.preset === PresetType.SOCIAL} 
              icon={<Users size={20}/>} 
              title="Social Media" 
              desc="Easy to type" 
              onClick={() => applyPreset(PresetType.SOCIAL)} 
            />
            <PresetBtn 
              active={options.preset === PresetType.DEVELOPER} 
              icon={<Code size={20}/>} 
              title="Developer" 
              desc="No ambiguous chars" 
              onClick={() => applyPreset(PresetType.DEVELOPER)} 
            />
            <PresetBtn 
              active={options.preset === PresetType.ENTERPRISE} 
              icon={<Briefcase size={20}/>} 
              title="Enterprise" 
              desc="Strict requirements" 
              onClick={() => applyPreset(PresetType.ENTERPRISE)} 
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
            <div className="flex p-1 bg-slate-50 dark:bg-slate-800/50 rounded-lg overflow-x-auto">
              {[
                { label: 'Random', type: PasswordType.RANDOM, icon: <Hash size={16} /> },
                { label: 'Phonetic', type: PasswordType.PRONOUNCEABLE, icon: <Mic size={16} /> },
                { label: 'Phrase', type: PasswordType.MEMORABLE, icon: <Type size={16} /> },
                { label: 'PIN', type: PasswordType.PIN, icon: <Lock size={16} /> },
              ].map((t) => (
                <button
                  key={t.type}
                  onClick={() => handleChange('type', t.type)}
                  className={`flex-1 min-w-[80px] flex flex-col sm:flex-row items-center justify-center gap-1.5 py-2 px-1 text-[11px] sm:text-xs font-bold rounded-md transition-all ${
                    options.type === t.type
                      ? 'bg-white dark:bg-slate-700 text-brand-blue dark:text-brand-teal shadow-sm'
                      : 'text-slate-400 hover:text-slate-600 dark:hover:text-slate-300'
                  }`}
                >
                  {t.icon}
                  <span>{t.label}</span>
                </button>
              ))}
            </div>

            {/* Length */}
            <div className="space-y-3">
              <div className="flex justify-between items-center">
                <label className="text-xs font-bold text-slate-500 uppercase tracking-wider">Length</label>
                <div className="bg-brand-blue/10 dark:bg-brand-teal/20 text-brand-blue dark:text-brand-teal px-3 py-0.5 rounded-full font-mono font-bold text-xs">
                  {options.length}
                </div>
              </div>
              <input
                type="range"
                min="4"
                max={options.type === PasswordType.MEMORABLE ? 100 : 64}
                value={options.length}
                onChange={(e) => handleChange('length', parseInt(e.target.value))}
                className="w-full h-1.5 bg-slate-200 dark:bg-slate-700 rounded-lg appearance-none cursor-pointer accent-brand-blue dark:accent-brand-teal"
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
  <button onClick={onClick} className={`p-3 rounded-xl border-2 text-left transition-all hover:scale-[1.02] active:scale-95 ${active ? 'border-brand-blue bg-brand-blue/5 dark:bg-brand-blue/10 dark:border-brand-teal' : 'border-slate-100 dark:border-slate-800 bg-white dark:bg-slate-800/50 hover:border-brand-blue/30'}`}>
    <div className={`${active ? 'text-brand-blue dark:text-brand-teal' : 'text-slate-400'}`}>{icon}</div>
    <div className={`text-sm font-bold mt-2 ${active ? 'text-slate-800 dark:text-white' : 'text-slate-600 dark:text-slate-400'}`}>{title}</div>
    <div className="text-[10px] text-slate-400 dark:text-slate-500 leading-tight mt-0.5">{desc}</div>
  </button>
);

const Toggle: React.FC<{ label: string; checked: boolean; onChange: (c: boolean) => void; fullWidth?: boolean; small?: boolean }> = ({
  label,
  checked,
  onChange,
  fullWidth,
  small
}) => (
  <label className={`flex items-center justify-between cursor-pointer group rounded-lg transition-colors ${fullWidth ? 'w-full' : ''} ${small ? 'py-1' : 'p-2 bg-slate-50 dark:bg-slate-800/50 border border-transparent hover:border-slate-200 dark:hover:border-slate-700'}`}>
    <span className={`text-xs font-bold transition-colors ${checked ? 'text-slate-700 dark:text-slate-200' : 'text-slate-400 dark:text-slate-500'}`}>
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
        className={`w-8 h-5 rounded-full transition-all duration-300 ${
          checked ? 'bg-brand-blue dark:bg-brand-teal' : 'bg-slate-200 dark:bg-slate-700'
        }`}
      />
      <div
        className={`absolute top-1 left-1 bg-white w-3 h-3 rounded-full transition-all duration-300 shadow-sm ${
          checked ? 'translate-x-3' : 'translate-x-0'
        }`}
      />
    </div>
  </label>
);

export default Controls;
