export enum PasswordType {
  RANDOM = 'random',
  MEMORABLE = 'memorable',
  PIN = 'pin',
  PRONOUNCEABLE = 'pronounceable'
}

export enum PresetType {
  CUSTOM = 'custom',
  BANKING = 'banking',
  SOCIAL = 'social',
  DEVELOPER = 'developer',
  ENTERPRISE = 'enterprise',
  WIFI = 'wifi',
  READABLE = 'readable',
  PIN_4 = 'pin-4',
  PIN_6 = 'pin-6',
  API_KEY = 'api-key',
  STREAMING = 'streaming',
  STANDARD = 'standard',
  PASSPHRASE = 'passphrase'
}

export interface PasswordOptions {
  // Core
  length: number;
  type: PasswordType;
  preset: PresetType;

  // Character Sets
  useUppercase: boolean;
  useLowercase: boolean;
  useNumbers: boolean;
  useSymbols: boolean;

  // Advanced Constraints
  excludeSimilar: boolean;
  noSymbolsAtEdges: boolean;
  minNumbers: number;
  minSymbols: number;
}

export interface PasswordStrength {
  score: number; // 0-4
  entropy: number; // bits
  crackTimeDisplay: string;
  guesses: number;
  verdict: 'Critical' | 'Weak' | 'Fair' | 'Strong' | 'Unbreakable';
}

export const DEFAULT_OPTIONS: PasswordOptions = {
  length: 16,
  type: PasswordType.RANDOM,
  preset: PresetType.CUSTOM,
  useUppercase: true,
  useLowercase: true,
  useNumbers: true,
  useSymbols: true,
  excludeSimilar: false,
  noSymbolsAtEdges: false,
  minNumbers: 1,
  minSymbols: 1
};
