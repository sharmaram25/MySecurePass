# MySecurePass

![MySecurePass App Showcase](https://via.placeholder.com/1200x600/0f172a/3b82f6?text=MySecurePass+Interface+Preview)

<div align="center">
  
  [![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](https://opensource.org/licenses/MIT)
  [![Security: Zero Knowledge](https://img.shields.io/badge/Security-Zero%20Knowledge-success.svg)](https://en.wikipedia.org/wiki/Zero-knowledge_proof)
  [![PWA: Ready](https://img.shields.io/badge/PWA-Installable-purple.svg)](https://web.dev/progressive-web-apps/)
  [![Stack: React + Vite](https://img.shields.io/badge/Tech-React%20%7C%20Vite%20%7C%20Tailwind-38bdf8.svg)](https://reactjs.org/)

  <p align="center">
    <strong>A military-grade, client-side entropy engine for the modern web.</strong>
  </p>
</div>

---

## 🛡️ Overview

**MySecurePass** is a security-first Progressive Web Application (PWA) designed to generate cryptographically secure credentials entirely within the user's browser. Unlike traditional generators, it employs an advanced entropy estimation engine to analyze password strength against offline fast-attack scenarios (GPU clusters).

Built with a philosophy of **"Trust through Architecture"**, no sensitive data is ever transmitted to a server. The application is stateless, serverless, and functions completely offline.

## ✨ Core Features

### 🔐 Advanced Cryptography Engine
- **Entropy-First Logic**: Passwords are generated using `window.crypto.getRandomValues` for true randomness.
- **Robust Analysis**: Real-time feedback on bit-strength and estimated crack time against ~100 GH/s offline attacks.
- **Smart Constraints**: 
  - Exclusion of visual lookalikes (`l`/`1`, `O`/`0`).
  - Positional security (preventing symbols at edges).
  - Minimum character distribution enforcement.

### 🧠 Intelligent Context Presets
- **Banking Mode**: Maximized entropy, 24+ chars, strict symbol requirements.
- **Developer Mode**: High complexity but excludes ambiguous characters for CLI ease.
- **Pronounceable Mode**: Uses phonetic CVC (Consonant-Vowel-Consonant) algorithms for memorable yet strong credentials.
- **Social Mode**: Balanced for mobile typing ease.

### 🎨 Fluid UX/UI
- **Glassmorphism Design**: A premium, "alive" interface with dynamic gradients and physics-based animations (Framer Motion).
- **Adaptive Theming**: Seamless automatic switching between **Solar Light** and **Deep Navy Dark** modes.
- **PWA Capabilities**: Installable on iOS/Android/Desktop with full offline support.

## 🏗️ Technical Architecture

This project adopts a **Zero-Knowledge Architecture**.

| Component | Technology | Description |
|-----------|------------|-------------|
| **Frontend** | React 18 + Vite | High-performance, component-based UI. |
| **Styling** | Tailwind CSS | Utility-first styling with custom animations. |
| **Motion** | Framer Motion | Physics-based layout transitions and micro-interactions. |
| **Security** | Web Crypto API | Native browser CSPRNG for entropy generation. |
| **Icons** | Lucide React | Lightweight, consistent SVG iconography. |
| **Deploy** | Netlify | Edge-ready static distribution. |

```typescript
// Sample Entropy Calculation Logic (Simplified)
const calculateEntropy = (poolSize: number, length: number): number => {
  return Math.log2(Math.pow(poolSize, length));
};
// Actual implementation includes penalty deductions for patterns, 
// repetitions, and sequence detection.
```

## 🚀 Getting Started

To run this project locally for development or auditing purposes:

1. **Clone the repository**
   ```bash
   git clone https://github.com/yourusername/my-secure-pass.git
   ```

2. **Install dependencies**
   ```bash
   npm install
   ```

3. **Start the development server**
   ```bash
   npm run dev
   ```

4. **Build for production**
   ```bash
   npm run build
   ```

## 🔒 Security Disclosure

*   **Client-Side Execution**: All generation happens in your browser's memory.
*   **No Persistence**: Passwords are never stored in `localStorage`, `cookies`, or sent to any API.
*   **No Analytics**: Input fields are strictly excluded from any form of telemetry.

## 👨‍💻 Creator

Designed and engineered by **Ram Sharma**.

---

<div align="center">
  <sub>Built with precision. Engineered for trust.</sub>
</div>
