import { useCallback, useEffect, useState } from 'react';
import { notificationApi } from '../lib/api';
import { onLive } from './useLiveEvents';

/**
 * Unread count for the bell badge. Updates instantly from live events, and every 60 s as a fallback
 * (and when the tab becomes visible) in case the live connection is down.
 */
export default function useNotifications(userId, onNew) {
  const [unread, setUnread] = useState(0);
  const refresh = useCallback(async () => {
    if (!userId) return;
    try { setUnread((await notificationApi.unread()).unread || 0); } catch { /* keep last value */ }
  }, [userId]);

  useEffect(() => {
    refresh();
    const id = setInterval(() => { if (document.visibilityState === 'visible') refresh(); }, 60000);
    const off = onLive('notification', (n) => { setUnread((u) => u + 1); onNew?.(n); });
    const vis = () => { if (document.visibilityState === 'visible') refresh(); };
    document.addEventListener('visibilitychange', vis);
    window.addEventListener('agritech:notifications-read', refresh);
    return () => {
      clearInterval(id);
      off();
      document.removeEventListener('visibilitychange', vis);
      window.removeEventListener('agritech:notifications-read', refresh);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [userId, refresh]);

  return { unread, refresh };
}

export const notificationsRead = () => window.dispatchEvent(new Event('agritech:notifications-read'));
