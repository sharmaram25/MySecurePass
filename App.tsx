import React, { useState, useEffect } from 'react';
import { Shield, Moon, Sun } from 'lucide-react';
import { Toaster } from 'react-hot-toast';
import { generatePassword, analyzePassword } from './utils/crypto';
import { DEFAULT_OPTIONS, PasswordOptions } from './types';
import PasswordDisplay from './components/PasswordDisplay';
import Controls from './components/Controls';
import StrengthMeter from './components/StrengthMeter';
import { LayoutGroup, motion } from 'framer-motion';

const App: React.FC = () => {
  const [options, setOptions] = useState<PasswordOptions>(DEFAULT_OPTIONS);
  const [password, setPassword] = useState('');
  const [strength, setStrength] = useState(analyzePassword(''));
  const [isDark, setIsDark] = useState(false);

  // Theme Logic
  useEffect(() => {
    const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)');
    const applyTheme = (dark: boolean) => {
      setIsDark(dark);
      if (dark) {
        document.documentElement.classList.add('dark');
      } else {
        document.documentElement.classList.remove('dark');
      }
    };
    applyTheme(mediaQuery.matches);
    const listener = (e: MediaQueryListEvent) => applyTheme(e.matches);
    mediaQuery.addEventListener('change', listener);
    return () => mediaQuery.removeEventListener('change', listener);
  }, []);

  const toggleTheme = () => {
    const newDark = !isDark;
    setIsDark(newDark);
    if (newDark) document.documentElement.classList.add('dark');
    else document.documentElement.classList.remove('dark');
  };

  // Initial generation
  useEffect(() => {
    handleGenerate();
  }, []);

  // Update logic when options change
  useEffect(() => {
    handleGenerate();
  }, [options]);

  const handleGenerate = () => {
    const newPassword = generatePassword(options);
    setPassword(newPassword);
    setStrength(analyzePassword(newPassword));
  };

  return (
    <LayoutGroup>
      <div className="min-h-full w-full flex flex-col items-center p-4 md:p-6 overflow-y-auto">
        <Toaster position="top-center" toastOptions={{
          style: {
            background: isDark ? 'rgba(30, 41, 59, 0.9)' : 'rgba(255, 255, 255, 0.9)',
            color: isDark ? '#fff' : '#1e293b',
            backdropFilter: 'blur(10px)',
            boxShadow: '0 10px 15px -3px rgba(0, 0, 0, 0.3)',
            borderRadius: '1rem',
          },
        }} />
        
        {/* Navbar / Theme Switcher */}
        <div className="w-full max-w-[460px] flex justify-end mb-4">
          <button 
            onClick={toggleTheme}
            className="p-2 rounded-full bg-white/50 dark:bg-slate-800/50 hover:bg-white dark:hover:bg-slate-700 backdrop-blur-sm shadow-sm transition-all text-slate-600 dark:text-slate-300"
            aria-label="Toggle Theme"
          >
            {isDark ? <Sun size={20} /> : <Moon size={20} />}
          </button>
        </div>

        {/* Hero Section */}
        <header className="flex flex-col items-center gap-4 mb-8 animate-float text-center">
          <div className="relative">
            <div className="absolute inset-0 bg-brand-blue blur-2xl opacity-20 dark:opacity-40 rounded-full animate-pulse-slow"></div>
            <div className="relative bg-gradient-to-br from-white to-slate-100 dark:from-slate-800 dark:to-slate-900 p-4 rounded-3xl shadow-xl shadow-brand-blue/10 dark:shadow-brand-blue/20 border border-white dark:border-slate-700 ring-1 ring-slate-100 dark:ring-slate-800">
              <Shield className="text-brand-blue dark:text-brand-teal fill-brand-blue/10 dark:fill-brand-teal/10" size={48} strokeWidth={2} />
            </div>
          </div>
          <div>
            <h1 className="text-3xl font-extrabold text-slate-800 dark:text-white tracking-tight">
              MySecurePass
            </h1>
            <div className="flex items-center justify-center gap-2 mt-1.5">
              <span className="h-1.5 w-1.5 rounded-full bg-brand-green animate-pulse"></span>
              <p className="text-xs font-bold text-slate-500 dark:text-slate-400 tracking-widest uppercase">
                Military-Grade Encryption
              </p>
            </div>
          </div>
        </header>

        {/* Main Glass Card */}
        <main className="w-full max-w-[460px] relative z-10 mb-8">
          <div className="absolute -inset-0.5 bg-gradient-to-b from-brand-blue/10 to-brand-green/10 dark:from-brand-blue/20 dark:to-brand-teal/10 rounded-[2.2rem] blur-xl -z-10" />
          
          <motion.div layout className="bg-white/80 dark:bg-slate-900/80 backdrop-blur-xl rounded-[2rem] shadow-2xl shadow-slate-200/50 dark:shadow-black/50 border border-white/50 dark:border-slate-700/50 p-6 md:p-8 space-y-8 transition-colors duration-300">
            
            <div className="space-y-6">
              <PasswordDisplay 
                password={password} 
                strength={strength}
                onRegenerate={handleGenerate} 
              />
              <StrengthMeter strength={strength} />
            </div>

            <div className="h-px w-full bg-gradient-to-r from-transparent via-slate-200 dark:via-slate-700 to-transparent" />

            <Controls options={options} setOptions={setOptions} />
            
          </motion.div>
        </main>

        {/* Footer / Credits */}
        <div className="text-center opacity-60 hover:opacity-100 transition-opacity duration-500 pb-4">
           <p className="text-[10px] text-slate-500 dark:text-slate-400 font-medium">
             Zero-Knowledge Architecture. No passwords stored.
           </p>
        </div>
      </div>
    </LayoutGroup>
  );
};

export default App;
