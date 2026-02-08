import React, { useState, useEffect } from 'react';
import { Copy, RefreshCw, Check, Eye, EyeOff } from 'lucide-react';
import { motion, AnimatePresence } from 'framer-motion';
import { PasswordStrength } from '../types';
import toast from 'react-hot-toast';

interface Props {
  password: string;
  strength: PasswordStrength;
  onRegenerate: () => void;
}

const PasswordDisplay: React.FC<Props> = ({ password, strength, onRegenerate }) => {
  const [copied, setCopied] = useState(false);
  const [isVisible, setIsVisible] = useState(true);

  useEffect(() => {
    if (copied) {
      const timer = setTimeout(() => setCopied(false), 2000);
      return () => clearTimeout(timer);
    }
  }, [copied]);

  const handleCopy = async () => {
    await navigator.clipboard.writeText(password);
    setCopied(true);
    toast.success('Password copied securely');
  };

  // Dynamic glow based on strength
  const getGlowColor = () => {
    if (strength.score >= 4) return 'shadow-brand-green/20 dark:shadow-brand-green/10 border-brand-green/30';
    if (strength.score === 3) return 'shadow-brand-blue/20 dark:shadow-brand-blue/10 border-brand-blue/30';
    return 'shadow-slate-200 dark:shadow-black/20 border-slate-100 dark:border-slate-700';
  };

  return (
    <div className="space-y-6">
      {/* Password Container */}
      <div className={`relative bg-slate-50 dark:bg-slate-950 rounded-2xl p-6 shadow-lg transition-all duration-300 border ${getGlowColor()}`}>
        
        {/* Visibility Toggle */}
        <button 
          onClick={() => setIsVisible(!isVisible)}
          className="absolute top-3 right-3 text-slate-500 hover:text-brand-blue dark:text-slate-400 dark:hover:text-brand-teal transition-colors p-2 hover:bg-slate-200 dark:hover:bg-slate-800 rounded-full focus:outline-none focus:ring-2 focus:ring-brand-blue/50"
          aria-label={isVisible ? "Hide password" : "Show password"}
        >
          {isVisible ? <EyeOff size={20}/> : <Eye size={20}/>}
        </button>

        {/* Text Display */}
        <div className="min-h-[4rem] flex items-center justify-center text-center break-all pt-2">
          <AnimatePresence mode="wait">
             <motion.p
              key={password}
              initial={{ opacity: 0, scale: 0.98 }}
              animate={{ opacity: 1, scale: 1 }}
              exit={{ opacity: 0, scale: 1.02 }}
              className={`font-mono text-3xl sm:text-4xl md:text-5xl text-slate-900 dark:text-white font-bold tracking-tight w-full ${!isVisible ? 'blur-md select-none opacity-40' : ''}`}
            >
              {password}
            </motion.p>
          </AnimatePresence>
        </div>
      </div>

      {/* Action Buttons */}
      <div className="grid grid-cols-5 gap-3">
        {/* Regenerate - Secondary Action */}
        <button
          onClick={onRegenerate}
          className="col-span-2 flex items-center justify-center gap-2 bg-white dark:bg-slate-800 hover:bg-slate-50 dark:hover:bg-slate-700 text-slate-700 dark:text-slate-200 hover:text-brand-blue dark:hover:text-white py-4 rounded-xl font-semibold transition-all active:scale-95 border border-slate-200 dark:border-slate-700 shadow-sm focus:outline-none focus:ring-2 focus:ring-slate-400"
        >
          <RefreshCw size={20} className="transition-transform active:rotate-180" />
          <span className="text-sm md:text-base">New</span>
        </button>
        
        {/* Copy - Primary Action */}
        <button
          onClick={handleCopy}
          className={`col-span-3 flex items-center justify-center gap-2 py-4 rounded-xl font-bold text-white transition-all active:scale-95 shadow-lg shadow-brand-blue/20 dark:shadow-brand-teal/20 focus:outline-none focus:ring-2 focus:ring-brand-blue/50 ${
            copied 
              ? 'bg-slate-800 dark:bg-slate-700 cursor-default' 
              : 'bg-gradient-to-r from-brand-blue to-brand-darkBlue dark:from-brand-blue dark:to-brand-teal hover:brightness-110'
          }`}
        >
          {copied ? <Check size={22} /> : <Copy size={22} />}
          <span className="text-sm md:text-base">{copied ? 'Copied' : 'Copy Securely'}</span>
        </button>
      </div>
    </div>
  );
};

export default PasswordDisplay;
