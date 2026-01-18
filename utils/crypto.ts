import { PasswordOptions, PasswordType, PasswordStrength } from '../types';

// --- Constants ---
const CHARS_UPPER = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ';
const CHARS_LOWER = 'abcdefghijklmnopqrstuvwxyz';
const CHARS_NUM = '0123456789';
const CHARS_SYM = '!@#$%^&*()_+-=[]{}|;:,.<>?';
const SIMILAR_CHARS = 'Il1O0';
const VOWELS = 'aeiou';
const CONSONANTS = 'bcdfghjklmnpqrstvwxyz';

// Word list for memorable passwords (expanded)
const WORD_LIST = [
  'apple', 'brave', 'crisp', 'delta', 'eagle', 'flame', 'grape', 'house', 'igloo', 'jolly',
  'kite', 'lemon', 'mango', 'noble', 'ocean', 'piano', 'quiet', 'rider', 'stone', 'tiger',
  'unity', 'vivid', 'whale', 'xenon', 'yacht', 'zebra', 'amber', 'block', 'charm', 'dusk',
  'elite', 'frost', 'giant', 'haven', 'inlet', 'jewel', 'knack', 'lunar', 'misty', 'nexus',
  'orbit', 'pride', 'quest', 'realm', 'solar', 'trace', 'urban', 'vault', 'world', 'yield',
  'breeze', 'canyon', 'drift', 'ember', 'falcon', 'glider', 'harbor', 'island', 'jungle',
  'laser', 'matrix', 'nebula', 'oasis', 'pilot', 'quartz', 'radar', 'sonic', 'terra', 'ultra'
];

// --- Crypto Helpers ---

const getRandomByte = (): number => {
  const arr = new Uint8Array(1);
  window.crypto.getRandomValues(arr);
  return arr[0];
};

const getRandomInt = (max: number): number => {
  if (max === 0) return 0;
  const maxVal = 256 - (256 % max);
  let r;
  do {
    r = getRandomByte();
  } while (r >= maxVal);
  return r % max;
};

const getRandomChar = (charset: string): string => {
  if (!charset) return '';
  return charset[getRandomInt(charset.length)];
};

// --- Generation Logic ---

const generateRandomPassword = (options: PasswordOptions): string => {
  let charset = '';
  const forcedChars: string[] = [];
  
  // Build charset and collect requirements
  if (options.useLowercase) charset += CHARS_LOWER;
  if (options.useUppercase) charset += CHARS_UPPER;
  if (options.useNumbers) charset += CHARS_NUM;
  if (options.useSymbols) charset += CHARS_SYM;

  if (options.excludeSimilar) {
    charset = charset.split('').filter(c => !SIMILAR_CHARS.includes(c)).join('');
  }

  // Fallback
  if (!charset) charset = CHARS_LOWER;

  // Generate Base
  const passwordArr: string[] = [];
  for (let i = 0; i < options.length; i++) {
    passwordArr.push(getRandomChar(charset));
  }

  // Enforce Minimums (Injection Method)
  // We shuffle indices to inject requirements randomly so they don't always appear at the start
  let availableIndices = Array.from({ length: options.length }, (_, i) => i);
  
  // Shuffle helper
  const shuffle = (arr: any[]) => {
    for (let i = arr.length - 1; i > 0; i--) {
      const j = getRandomInt(i + 1);
      [arr[i], arr[j]] = [arr[j], arr[i]];
    }
  };
  shuffle(availableIndices);

  const inject = (set: string, count: number) => {
    let filteredSet = set;
    if (options.excludeSimilar) {
      filteredSet = set.split('').filter(c => !SIMILAR_CHARS.includes(c)).join('');
    }
    for (let k = 0; k < count; k++) {
      if (availableIndices.length > 0) {
        const idx = availableIndices.pop()!;
        passwordArr[idx] = getRandomChar(filteredSet);
      }
    }
  };

  if (options.useNumbers && options.minNumbers > 0) inject(CHARS_NUM, options.minNumbers);
  if (options.useSymbols && options.minSymbols > 0) inject(CHARS_SYM, options.minSymbols);
  if (options.useUppercase) inject(CHARS_UPPER, 1); // Ensure at least 1 if selected
  
  // Enforce Position Constraints (No Symbols at Edges)
  if (options.noSymbolsAtEdges && options.length > 1) {
    const isSymbol = (c: string) => CHARS_SYM.includes(c);
    const nonSymbolSet = (options.useLowercase ? CHARS_LOWER : '') + 
                         (options.useUppercase ? CHARS_UPPER : '') + 
                         (options.useNumbers ? CHARS_NUM : '');
    const safeSet = nonSymbolSet || CHARS_LOWER; // Fallback

    // Check Start
    if (isSymbol(passwordArr[0])) {
      passwordArr[0] = getRandomChar(safeSet);
    }
    // Check End
    if (isSymbol(passwordArr[passwordArr.length - 1])) {
      passwordArr[passwordArr.length - 1] = getRandomChar(safeSet);
    }
  }

  return passwordArr.join('');
};

const generatePronounceable = (options: PasswordOptions): string => {
  let password = '';
  // CVC Pattern (Consonant-Vowel-Consonant)
  while (password.length < options.length) {
    password += getRandomChar(CONSONANTS);
    if (password.length < options.length) password += getRandomChar(VOWELS);
  }
  
  // Apply casing
  if (options.useUppercase) {
    // Capitalize first letter
    password = password.charAt(0).toUpperCase() + password.slice(1);
    // Maybe capitalize random letters if length is long
    if (options.length > 8) {
       // simple logic: do nothing else to keep it readable
    }
  }

  // Inject numbers/symbols if requested, but try to keep pronounceability if possible
  // For this version, we append numbers/symbols to end to keep the word part clean,
  // or inject them between syllables.
  let suffix = '';
  if (options.useNumbers && options.minNumbers > 0) {
    for(let i=0; i<options.minNumbers; i++) suffix += getRandomChar(CHARS_NUM);
  }
  if (options.useSymbols && options.minSymbols > 0) {
    for(let i=0; i<options.minSymbols; i++) suffix += getRandomChar(CHARS_SYM);
  }

  return password.substring(0, options.length - suffix.length) + suffix;
};

const generateMemorable = (options: PasswordOptions): string => {
  const wordCount = Math.max(3, Math.floor(options.length / 5));
  const words = [];
  const separator = options.useSymbols ? (options.minSymbols > 0 ? '-' : '') : ' ';
  
  for (let i = 0; i < wordCount; i++) {
    let word = WORD_LIST[getRandomInt(WORD_LIST.length)];
    if (options.useUppercase) {
      word = word.charAt(0).toUpperCase() + word.slice(1);
    }
    words.push(word);
  }
  
  let result = words.join(separator);
  
  // Trim or pad to near length (memorable is loose on length usually)
  // But strict length was requested? Let's append numbers to meet length or trim.
  
  if (options.useNumbers) {
    while (result.length < options.length) {
      result += getRandomChar(CHARS_NUM);
    }
  }
  
  return result.substring(0, options.length + 5); // Allow slightly longer for memorable
};

export const generatePassword = (options: PasswordOptions): string => {
  if (options.type === PasswordType.PIN) {
    let pin = '';
    for (let i = 0; i < options.length; i++) {
      pin += CHARS_NUM[getRandomInt(CHARS_NUM.length)];
    }
    return pin;
  }

  if (options.type === PasswordType.MEMORABLE) return generateMemorable(options);
  if (options.type === PasswordType.PRONOUNCEABLE) return generatePronounceable(options);

  return generateRandomPassword(options);
};

// --- Analysis Logic ---

export const analyzePassword = (password: string): PasswordStrength => {
  if (!password) {
    return { score: 0, entropy: 0, crackTimeDisplay: '0s', guesses: 0, verdict: 'Critical' };
  }

  const length = password.length;
  
  // 1. Determine effective pool size
  let hasLower = false, hasUpper = false, hasNum = false, hasSym = false;
  if (/[a-z]/.test(password)) hasLower = true;
  if (/[A-Z]/.test(password)) hasUpper = true;
  if (/[0-9]/.test(password)) hasNum = true;
  if (/[^a-zA-Z0-9]/.test(password)) hasSym = true;

  let pool = 0;
  if (hasLower) pool += 26;
  if (hasUpper) pool += 26;
  if (hasNum) pool += 10;
  if (hasSym) pool += 33;

  // 2. Calculate Base Entropy
  let entropy = Math.log2(Math.pow(pool, length));

  // 3. Penalty Deductions (Pattern Recognition)
  // Repeats
  const repeatCount = length - new Set(password.split('')).size;
  entropy -= repeatCount * 1.5; // Penalty for repeats

  // Sequential chars (e.g., "123", "abc")
  let sequences = 0;
  for (let i = 0; i < length - 2; i++) {
    const code = password.charCodeAt(i);
    const next1 = password.charCodeAt(i+1);
    const next2 = password.charCodeAt(i+2);
    if (next1 === code + 1 && next2 === code + 2) sequences++;
  }
  entropy -= sequences * 3;

  // Dictionary check (simplified) for generic words
  // In a real app we'd load a bloom filter, here we just penalize if it looks like just letters
  if (pool === 26 || pool === 52) {
    entropy *= 0.8; // Reduce entropy for text-only passwords as they are likely words
  }

  if (entropy < 0) entropy = 0;

  // 4. Crack Time Calculation
  // Scenario: Offline Fast Attack (GPU cluster)
  // Assumption: 100 Billion guesses per second (1e11) - Modern standard for MD5/SHA1
  // For stronger hashing (bcrypt), this would be much lower, but we assume worst case (fast hash)
  const GUESSES_PER_SECOND = 1e11; 
  const guesses = Math.pow(2, entropy);
  const seconds = guesses / GUESSES_PER_SECOND;

  // 5. Verdict
  let verdict: PasswordStrength['verdict'] = 'Critical';
  let score = 0;

  if (entropy > 128) { verdict = 'Unbreakable'; score = 4; }
  else if (entropy > 80) { verdict = 'Strong'; score = 3; }
  else if (entropy > 60) { verdict = 'Fair'; score = 2; }
  else if (entropy > 35) { verdict = 'Weak'; score = 1; }

  return {
    score,
    entropy: Math.round(entropy),
    guesses,
    crackTimeDisplay: formatTime(seconds),
    verdict
  };
};

const formatTime = (seconds: number): string => {
  if (seconds < 1e-6) return 'Instantly';
  if (seconds < 60) return `${Math.ceil(seconds)} seconds`;
  if (seconds < 3600) return `${Math.ceil(seconds / 60)} minutes`;
  if (seconds < 86400) return `${Math.ceil(seconds / 3600)} hours`;
  if (seconds < 2592000) return `${Math.ceil(seconds / 86400)} days`;
  if (seconds < 31536000) return `${Math.ceil(seconds / 2592000)} months`;
  
  const years = seconds / 31536000;
  if (years < 100) return `${Math.round(years)} years`;
  if (years < 1000) return `${Math.round(years / 100) * 100} years`;
  if (years < 1e6) return `${(years / 1000).toFixed(1)}k years`;
  if (years < 1e9) return `${(years / 1e6).toFixed(1)}m years`;
  if (years < 1e12) return `${(years / 1e9).toFixed(1)}bn years`;
  return 'Trillions of years';
};
