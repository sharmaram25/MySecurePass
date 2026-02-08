import React from 'react';
import { motion } from 'framer-motion';
import { PasswordStrength } from '../types';

interface Props {
  strength: PasswordStrength;
}

const StrengthMeter: React.FC<Props> = ({ strength }) => {
  return (
    <div className="w-full space-y-2">
      <div className="flex justify-between items-end">
        <div className="flex flex-col">
          <span className="text-xs font-bold text-slate-500 dark:text-slate-400 uppercase tracking-wider mb-1">Estimated Crack Time</span>
          <span className="text-base md:text-lg font-mono font-bold text-slate-800 dark:text-white">{strength.crackTimeDisplay}</span>
        </div>
        <div className="text-right flex flex-col items-end">
          <span className="text-xs font-bold text-slate-500 dark:text-slate-400 uppercase tracking-wider mb-1">Entropy</span>
          <div className="flex items-center gap-2">
             <span className="text-sm text-slate-500 dark:text-slate-400 font-mono">{strength.entropy} bits</span>
             <span className={`text-base md:text-lg font-extrabold ${
                strength.score >= 4 ? 'text-brand-green' : 
                strength.score === 3 ? 'text-brand-blue dark:text-brand-teal' : 
                strength.score === 2 ? 'text-yellow-500' : 'text-red-500'
              }`}>
                {strength.verdict}
              </span>
          </div>
        </div>
      </div>

      {/* Meter */}
      <div className="relative h-3 w-full bg-slate-100 dark:bg-slate-800 rounded-full overflow-hidden">
        <motion.div
          className="h-full rounded-full"
          initial={false}
          animate={{ 
            width: `${Math.min(100, (strength.entropy / 128) * 100)}%`,
            backgroundColor: strength.score >= 4 ? '#10b981' : strength.score === 3 ? '#3b82f6' : strength.score === 2 ? '#facc15' : '#f87171'
          }}
          transition={{ type: "spring", stiffness: 100, damping: 20 }}
        />
        {/* Grid lines */}
        <div className="absolute inset-0 flex">
          {[1,2,3].map(i => <div key={i} className="flex-1 border-r border-white/20 dark:border-black/10"></div>)}
        </div>
      </div>
      
      <p className="text-xs text-slate-500 dark:text-slate-400 text-center mt-2 group relative cursor-help">
        Based on offline fast attack (~100 GH/s)
        <span className="absolute bottom-full left-1/2 -translate-x-1/2 mb-2 w-max px-2 py-1 bg-slate-800 text-white text-[10px] rounded opacity-0 group-hover:opacity-100 transition-opacity pointer-events-none">Standard brute-force benchmark</span>
      </p>
    </div>
  );
};

export default StrengthMeter;
