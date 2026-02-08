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
  // Update logic when options change with debounce
  useEffect(() => {
    const timeoutId = setTimeout(() => {
      handleGenerate();
    }, 200); // Debounce for 200ms for smoother slider experience

    return () => clearTimeout(timeoutId);
  }, [options]);

  const handleGenerate = () => {
    const newPassword = generatePassword(options);
    setPassword(newPassword);
    setStrength(analyzePassword(newPassword));
  };

  return (
    <LayoutGroup>
      <div className="min-h-[100dvh] w-full flex flex-col items-center p-4 md:p-6 overflow-y-auto">
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
        <div className="w-full max-w-5xl flex justify-end mb-4">
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
            <h1 className="text-3xl md:text-4xl font-extrabold text-slate-900 dark:text-white tracking-tight">
              MySecurePass
            </h1>
            <div className="flex items-center justify-center gap-2 mt-2">
              <span className="h-2 w-2 rounded-full bg-brand-green animate-pulse"></span>
              <p className="text-xs md:text-sm font-bold text-slate-600 dark:text-slate-400 tracking-widest uppercase">
                Military-Grade Encryption
              </p>
            </div>
          </div>
        </header>

        {/* Main Content Area */}
        <main className="w-full max-w-5xl relative z-10 mb-8">
           <div className="absolute -inset-0.5 bg-gradient-to-b from-brand-blue/10 to-brand-green/10 dark:from-brand-blue/20 dark:to-brand-teal/10 rounded-[2.2rem] blur-xl -z-10" />
           
           <motion.div layout className="bg-white/90 dark:bg-slate-900/90 backdrop-blur-2xl rounded-[2.5rem] shadow-2xl shadow-slate-200/50 dark:shadow-black/60 border border-white/60 dark:border-slate-700/60 p-6 md:p-10 transition-all duration-500 ease-in-out">
             
             <div className="grid grid-cols-1 md:grid-cols-2 gap-8 md:gap-12">
               {/* Left Column: Result & Strength */}
               <div className="space-y-6 flex flex-col justify-center">
                 <div className="md:sticky md:top-8 space-y-6">
                    <PasswordDisplay 
                      password={password} 
                      strength={strength}
                      onRegenerate={handleGenerate} 
                    />
                    <StrengthMeter strength={strength} />
                 </div>
               </div>

               {/* Right Column: Controls */}
               <div className="relative">
                  {/* Divider for mobile only */}
                  <div className="md:hidden h-px w-full bg-gradient-to-r from-transparent via-slate-200 dark:via-slate-700 to-transparent my-6" />
                  
                  {/* Vertical Divider for desktop */}
                  <div className="hidden md:block absolute left-[-1.5rem] top-0 bottom-0 w-px bg-gradient-to-b from-transparent via-slate-200 dark:via-slate-700 to-transparent" />

                  <Controls options={options} setOptions={setOptions} />
               </div>
             </div>
             
           </motion.div>
        </main>

        {/* Footer / Credits */}
        <div className="text-center opacity-80 hover:opacity-100 transition-opacity duration-500 pb-4">
           <p className="text-xs text-slate-600 dark:text-slate-400 font-medium">
             Zero-Knowledge Architecture. No passwords stored.
           </p>
        </div>
      </div>
    </LayoutGroup>
  );
};

export default App;
