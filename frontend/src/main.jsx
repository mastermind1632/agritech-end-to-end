import React from 'react';
import { createRoot } from 'react-dom/client';
import App from './App';
import './styles.css';
import './chat.css';
import './platform.css';
import './theme.css';
import './theme-dark.generated.css';
import './motion.css';
import { applyDisplay } from './lib/theme';
import { getLang } from './lib/i18n';

applyDisplay();
document.documentElement.lang = getLang();

createRoot(document.getElementById('root')).render(<React.StrictMode><App /></React.StrictMode>);

// Offline support (production builds only, so the dev server's hot reload is never cached).
if (import.meta.env.PROD && 'serviceWorker' in navigator) {
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('/sw.js').catch(() => { /* the app works without it */ });
  });
}
