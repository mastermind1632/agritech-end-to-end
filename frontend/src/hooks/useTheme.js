import { useEffect, useState } from 'react';
import { getTextSize, getTheme, listeners, resolvedTheme, setTextSize, setTheme } from '../lib/theme';

/** Current display preferences; re-renders when they change anywhere in the app. */
export default function useTheme() {
  const [state, set] = useState(() => ({ theme: getTheme(), shown: resolvedTheme(), textSize: getTextSize() }));
  useEffect(() => {
    listeners.add(set);
    return () => listeners.delete(set);
  }, []);
  return { ...state, setTheme, setTextSize };
}
