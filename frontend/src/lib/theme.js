// Display preferences: colour theme (light, dark or follow the device) and larger text.
// Saved on this device. index.html applies them before the first paint so the screen never flashes white.
const THEME_KEY = 'agritech_theme';
const TEXT_KEY = 'agritech_text_size';
export const listeners = new Set();

const media = () => (typeof window !== 'undefined' && window.matchMedia ? window.matchMedia('(prefers-color-scheme: dark)') : null);

export function getTheme() {
  try { return localStorage.getItem(THEME_KEY) || 'system'; } catch { return 'system'; }
}
export function getTextSize() {
  try { return localStorage.getItem(TEXT_KEY) || 'normal'; } catch { return 'normal'; }
}

/** The theme actually shown: 'light' or 'dark'. */
export function resolvedTheme(theme = getTheme()) {
  if (theme === 'dark' || theme === 'light') return theme;
  return media()?.matches ? 'dark' : 'light';
}

export function applyDisplay() {
  if (typeof document === 'undefined') return;
  const root = document.documentElement;
  const shown = resolvedTheme();
  root.dataset.theme = shown;
  root.dataset.text = getTextSize();
  root.style.colorScheme = shown;
  const meta = document.querySelector('meta[name="theme-color"]');
  if (meta) meta.setAttribute('content', shown === 'dark' ? '#0b1a15' : '#0d5c45');
}

function notify() { applyDisplay(); listeners.forEach((fn) => fn({ theme: getTheme(), shown: resolvedTheme(), textSize: getTextSize() })); }

let switching;
export function setTheme(theme) {
  try { localStorage.setItem(THEME_KEY, theme); } catch { /* ignore */ }
  if (typeof document !== 'undefined') {
    // Fade colours for a moment instead of flashing from one theme to the other.
    const root = document.documentElement;
    root.classList.add('theme-switching');
    clearTimeout(switching);
    switching = setTimeout(() => root.classList.remove('theme-switching'), 450);
  }
  notify();
}
export function setTextSize(size) {
  try { localStorage.setItem(TEXT_KEY, size); } catch { /* ignore */ }
  notify();
}

// Follow the device when the theme is 'system'.
const m = media();
if (m) {
  const onChange = () => { if (getTheme() === 'system') notify(); };
  if (m.addEventListener) m.addEventListener('change', onChange); else if (m.addListener) m.addListener(onChange);
}
