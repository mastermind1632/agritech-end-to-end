import { useEffect, useState } from 'react';

/** Form state that survives a lost connection or a closed tab (offline drafts), kept per key. */
export default function useDraft(key, initial) {
  const storageKey = 'agritech_draft_' + key;
  const [value, setValue] = useState(() => {
    try { const saved = localStorage.getItem(storageKey); return saved ? { ...initial, ...JSON.parse(saved) } : initial; } catch { return initial; }
  });
  useEffect(() => {
    try { localStorage.setItem(storageKey, JSON.stringify(value)); } catch { /* storage full or blocked */ }
  }, [storageKey, value]);
  const clear = () => { try { localStorage.removeItem(storageKey); } catch { /* ignore */ } setValue(initial); };
  return [value, setValue, clear];
}
