import { useState } from 'react';
import Generate from './components/Generate';
import Header from './components/Header';
import Social from './components/Social';
import './css/security.css';

function App() {

  const [darkMode, setDarkMode] = useState(true);
  const [isTransitioning, setIsTransitioning] = useState(false);

  function handleOnClick(){
    setIsTransitioning(true);
    
    setTimeout(() => {
      setDarkMode(!darkMode);
      
      setTimeout(() => {
        setIsTransitioning(false);
      }, 800);
    }, 100);
  }

  return (
    <>
      <div
      className=
      {
        `security-background min-h-screen transition-all duration-800 ${
          isTransitioning ? 'transitioning' : ''
        } ${
          (darkMode == false) 
            ? "light-mode bg-gradient-to-br from-purple-50 via-indigo-50 to-blue-100 text-gray-800" 
            : "dark-mode bg-gradient-to-br from-gray-900 via-purple-900 to-indigo-900 text-gray-200"
        }`
      }
      >
        {/* Floating Security Elements */}
        <div className="floating-elements">
          {/* Shield Icons */}
          <div className="security-element shield-1">
            <svg width="48" height="48" viewBox="0 0 24 24" fill="currentColor">
              <path d="M12 1L3 5v6c0 5.55 3.84 10.74 9 12 5.16-1.26 9-6.45 9-12V5l-9-4z"/>
            </svg>
          </div>
          <div className="security-element shield-2 pulse">
            <svg width="48" height="48" viewBox="0 0 24 24" fill="currentColor">
              <path d="M12 1L3 5v6c0 5.55 3.84 10.74 9 12 5.16-1.26 9-6.45 9-12V5l-9-4zM10 17l-4-4 1.41-1.41L10 14.17l6.59-6.59L18 9l-8 8z"/>
            </svg>
          </div>
          <div className="security-element shield-3">
            <svg width="32" height="32" viewBox="0 0 24 24" fill="currentColor">
              <path d="M12 1L3 5v6c0 5.55 3.84 10.74 9 12 5.16-1.26 9-6.45 9-12V5l-9-4zM18 11c0 3.49-2.54 6.65-6 7.35V8h2v2h2V8h2v3z"/>
            </svg>
          </div>
          
          {/* Lock Icons */}
          <div className="security-element lock-1">
            <svg width="40" height="40" viewBox="0 0 24 24" fill="currentColor">
              <path d="M18 8h-1V6c0-2.76-2.24-5-5-5S7 3.24 7 6v2H6c-1.1 0-2 .9-2 2v10c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V10c0-1.1-.9-2-2-2zM9 6c0-1.66 1.34-3 3-3s3 1.34 3 3v2H9V6zm9 14H6V10h12v10zm-6-3c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2z"/>
            </svg>
          </div>
          <div className="security-element lock-2">
            <svg width="35" height="35" viewBox="0 0 24 24" fill="currentColor">
              <path d="M12 17c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2zm6-9h-1V6c0-2.76-2.24-5-5-5S7 3.24 7 6h1.9c0-1.71 1.39-3.1 3.1-3.1 1.71 0 3.1 1.39 3.1 3.1v2H6c-1.1 0-2 .9-2 2v10c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V10c0-1.1-.9-2-2-2zM18 20H6V10h12v10z"/>
            </svg>
          </div>
          <div className="security-element lock-3 pulse">
            <svg width="28" height="28" viewBox="0 0 24 24" fill="currentColor">
              <path d="M6 10v-4c0-3.31 2.69-6 6-6s6 2.69 6 6v4h2v14H4V10h2zm2 0h8v-4c0-2.21-1.79-4-4-4s-4 1.79-4 4v4z"/>
            </svg>
          </div>
          
          {/* Key Icons */}
          <div className="security-element key-1 pulse">
            <svg width="32" height="32" viewBox="0 0 24 24" fill="currentColor">
              <path d="M12.65 10C11.83 7.67 9.61 6 7 6c-3.31 0-6 2.69-6 6s2.69 6 6 6c2.61 0 4.83-1.67 5.65-4H17v4h4v-4h2v-4H12.65zM7 14c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 2-.9 2-2 2z"/>
            </svg>
          </div>
          <div className="security-element key-2">
            <svg width="28" height="28" viewBox="0 0 24 24" fill="currentColor">
              <path d="M7 14c-1.66 0-3-1.34-3-3 0-1.66 1.34-3 3-3s3 1.34 3 3c0 1.66-1.34 3-3 3zm0-4c-.55 0-1 .45-1 1s.45 1 1 1 1-.45 1-1-.45-1-1-1zm12.2-1.49c-.27-.24-.52-.44-.78-.6-.4-.25-.87-.36-1.38-.36h-8.52c-.92 0-1.65.81-1.65 1.81v.57c0 .42.34.76.76.76s.76-.34.76-.76v-.57c0-.14.14-.28.32-.28h8.52c.18 0 .35.05.5.15.12.08.22.19.3.31l.02.02.69 1.05.69-1.05.02-.02c.08-.12.18-.23.3-.31z"/>
            </svg>
          </div>
          <div className="security-element key-3">
            <svg width="25" height="25" viewBox="0 0 24 24" fill="currentColor">
              <path d="M21 10h-8.35C11.83 7.67 9.61 6 7 6c-3.31 0-6 2.69-6 6s2.69 6 6 6c2.61 0 4.83-1.67 5.65-4H13v2h2v-2h2v2h2v-2h2v-4zM7 14c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 2-.9 2-2 2z"/>
            </svg>
          </div>
          
          {/* Fingerprint Icons */}
          <div className="security-element fingerprint-1">
            <svg width="40" height="40" viewBox="0 0 24 24" fill="currentColor">
              <path d="M17.81 4.47c-.08 0-.16-.02-.23-.06C15.66 3.42 14 3 12.01 3c-1.98 0-3.86.47-5.57 1.41-.24.13-.54.04-.68-.2-.13-.24-.04-.55.2-.68C7.82 2.52 9.86 2 12.01 2c2.13 0 3.99.47 6.03 1.52.25.13.34.43.21.67-.09.18-.26.28-.44.28zM3.5 9.72c-.1 0-.2-.03-.29-.09-.23-.16-.28-.47-.12-.7.99-1.4 2.25-2.5 3.75-3.27C9.98 4.04 14 4.03 17.15 5.65c1.5.77 2.76 1.86 3.75 3.25.16.22.11.54-.12.7-.23.16-.54.11-.7-.12-.9-1.26-2.04-2.25-3.39-2.94-2.87-1.47-6.54-1.47-9.4.01-1.36.7-2.5 1.7-3.4 2.96-.08.14-.23.21-.39.21zm6.25 12.07c-.13 0-.26-.05-.35-.15-.87-.87-1.34-1.43-2.01-2.64-.69-1.23-1.05-2.73-1.05-4.34 0-2.97 2.54-5.39 5.66-5.39s5.66 2.42 5.66 5.39c0 .28-.22.5-.5.5s-.5-.22-.5-.5c0-2.42-2.09-4.39-4.66-4.39-2.57 0-4.66 1.97-4.66 4.39 0 1.44.32 2.77.93 3.85.64 1.15 1.08 1.64 1.85 2.42.19.2.19.51 0 .71-.11.1-.24.15-.37.15zm7.17-1.85c-1.19 0-2.24-.3-3.1-.89-1.49-1.01-2.38-2.65-2.38-4.39 0-.28.22-.5.5-.5s.5.22.5.5c0 1.41.72 2.74 1.94 3.56.71.48 1.54.71 2.54.71.24 0 .64-.03 1.04-.1.27-.05.55.13.6.4.05.27-.13.55-.4.6-.55.09-1.17.11-1.24.11zM14.91 22c-.04 0-.09-.01-.13-.02-1.59-.44-2.63-1.03-3.72-2.1-1.4-1.39-2.17-3.24-2.17-5.22 0-1.62 1.38-2.94 3.08-2.94 1.7 0 3.08 1.32 3.08 2.94 0 1.07.93 1.94 2.08 1.94s2.08-.87 2.08-1.94c0-3.77-3.25-6.83-7.25-6.83-2.84 0-5.44 1.58-6.61 4.03-.39.81-.59 1.76-.59 2.8 0 .78.07 2.01.67 3.61.1.26-.03.55-.29.64-.26.1-.55-.04-.64-.29-.49-1.31-.73-2.61-.73-3.96 0-1.2.23-2.29.68-3.24 1.33-2.79 4.28-4.6 7.51-4.6 4.55 0 8.25 3.51 8.25 7.83 0 1.62-1.38 2.94-3.08 2.94s-3.08-1.32-3.08-2.94c0-1.07-.93-1.94-2.08-1.94s-2.08.87-2.08 1.94c0 1.71.66 3.31 1.87 4.51.95.94 1.86 1.46 3.27 1.85.27.07.42.35.35.61-.05.23-.26.38-.47.38z"/>
            </svg>
          </div>
          <div className="security-element fingerprint-2 pulse">
            <svg width="32" height="32" viewBox="0 0 24 24" fill="currentColor">
              <path d="M12 1C8.69 1 6 3.69 6 7v2.59l-2.29 2.29c-.39.39-.39 1.02 0 1.41L12 21.59l8.29-8.29c.39-.39.39-1.02 0-1.41L18 9.59V7c0-3.31-2.69-6-6-6zm4 8.59V7c0-2.21-1.79-4-4-4s-4 1.79-4 4v2.59L12 13.41l4-3.82z"/>
            </svg>
          </div>
          
          {/* Binary Code Elements */}
          <div className="security-element binary-1 binary-code">
            101010
          </div>
          <div className="security-element binary-2 binary-code">
            110011
          </div>
          <div className="security-element binary-3 binary-code">
            001101
          </div>
          
          {/* WiFi Security Icons */}
          <div className="security-element wifi-1">
            <svg width="32" height="32" viewBox="0 0 24 24" fill="currentColor">
              <path d="M1 9l2 2c4.97-4.97 13.03-4.97 18 0l2-2C16.93 2.93 7.08 2.93 1 9zm8 8l3 3 3-3c-1.65-1.66-4.34-1.66-6 0zm-4-4l2 2c2.76-2.76 7.24-2.76 10 0l2-2C15.14 9.14 8.87 9.14 5 13z"/>
            </svg>
          </div>
          <div className="security-element wifi-2 pulse">
            <svg width="27" height="27" viewBox="0 0 24 24" fill="currentColor">
              <path d="M12 6c3.79 0 7.17 2.13 8.82 5.5.39.8 1.28 1.12 2.08.73.8-.39 1.12-1.28.73-2.08C21.58 6.2 17.09 3 12 3s-9.58 3.2-11.63 7.15c-.39.8-.07 1.69.73 2.08.8.39 1.69.07 2.08-.73C4.83 8.13 8.21 6 12 6zm0 3c2.21 0 4.21.9 5.66 2.34.58.58 1.52.58 2.1 0 .58-.58.58-1.52 0-2.1C17.64 7.12 14.95 6 12 6s-5.64 1.12-7.76 3.24c-.58.58-.58 1.52 0 2.1.58.58 1.52.58 2.1 0C7.79 9.9 9.79 9 12 9z"/>
            </svg>
          </div>
          
          {/* Enhanced Security Particles */}
          <div className="security-particles">
            <div className="particle"></div>
            <div className="particle"></div>
            <div className="particle"></div>
            <div className="particle"></div>
            <div className="particle"></div>
            <div className="particle"></div>
            <div className="particle"></div>
            <div className="particle"></div>
          </div>
        </div>
        
        {/* Main Content with Overlay */}
        <div className="content-overlay">
          <Header handleOnClick={handleOnClick} />
          <Generate darkMode={darkMode} />
          <Social/>
        </div>
      </div>
    </>
  )
}

export default App;
