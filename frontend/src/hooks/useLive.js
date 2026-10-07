import { useCallback, useEffect, useRef, useState } from 'react';

export default function useLive(fetcher, deps = [], { interval = 15000, enabled = true } = {}) {
  const [state, setState] = useState({ data: null, loading: true, error: '', updatedAt: null });
  const fetchRef = useRef(fetcher);
  fetchRef.current = fetcher;
  const alive = useRef(true);

  const refresh = useCallback(async (silent = true) => {
    if (!silent) setState((s) => ({ ...s, loading: true }));
    try {
      const data = await fetchRef.current();
      if (alive.current) setState({ data, loading: false, error: '', updatedAt: new Date() });
    } catch (e) {
      if (alive.current) setState((s) => ({ ...s, loading: false, error: e.message || 'Something went wrong' }));
    }
  }, []);

  useEffect(() => {
    alive.current = true;
    if (!enabled) return () => { alive.current = false; };
    refresh(false);
    const tick = () => { if (document.visibilityState === 'visible' && navigator.onLine) refresh(); };
    const id = interval ? setInterval(tick, interval) : null;
    document.addEventListener('visibilitychange', tick);
    window.addEventListener('online', tick);
    window.addEventListener('focus', tick);
    return () => {
      alive.current = false;
      if (id) clearInterval(id);
      document.removeEventListener('visibilitychange', tick);
      window.removeEventListener('online', tick);
      window.removeEventListener('focus', tick);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [enabled, interval, ...deps]);

  return { ...state, refresh };
}