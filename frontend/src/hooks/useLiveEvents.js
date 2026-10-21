import { useEffect } from 'react';
import { API, notificationApi } from '../lib/api';

/**
 * Opens one Server-Sent Events connection for the signed-in user and re-broadcasts each event in the page as
 * window event "agritech:live" ({ type, data }). Reconnects with a fresh ticket after errors or sleep.
 */
export default function useLiveEvents(userId) {
  useEffect(() => {
    if (!userId || typeof EventSource === 'undefined') return undefined;
    let source = null;
    let retry = null;
    let stopped = false;
    let delay = 3000;

    const emit = (type, data) => window.dispatchEvent(new CustomEvent('agritech:live', { detail: { type, data } }));

    const connect = async () => {
      if (stopped) return;
      try {
        const { ticket } = await notificationApi.ticket();
        if (stopped) return;
        source = new EventSource(`${API}/live/stream?ticket=${encodeURIComponent(ticket)}`);
        source.addEventListener('hello', () => { delay = 3000; emit('connected', {}); });
        ['notification', 'chat'].forEach((type) => source.addEventListener(type, (e) => {
          try { emit(type, JSON.parse(e.data)); } catch { emit(type, {}); }
        }));
        source.onerror = () => {
          source?.close();
          emit('disconnected', {});
          schedule();
        };
      } catch {
        schedule();
      }
    };
    const schedule = () => {
      if (stopped) return;
      clearTimeout(retry);
      retry = setTimeout(connect, delay);
      delay = Math.min(delay * 2, 60000);
    };
    const wake = () => { if (document.visibilityState === 'visible' && (!source || source.readyState === 2)) { delay = 1000; schedule(); } };

    connect();
    document.addEventListener('visibilitychange', wake);
    window.addEventListener('online', wake);
    return () => {
      stopped = true;
      clearTimeout(retry);
      source?.close();
      document.removeEventListener('visibilitychange', wake);
      window.removeEventListener('online', wake);
    };
  }, [userId]);
}

/** Subscribe a component to live events of one type. */
export function onLive(type, handler) {
  const listener = (e) => { if (e.detail?.type === type) handler(e.detail.data); };
  window.addEventListener('agritech:live', listener);
  return () => window.removeEventListener('agritech:live', listener);
}
