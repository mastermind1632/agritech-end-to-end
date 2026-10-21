import { useEffect, useState } from 'react';
import useLang from '../hooks/useLang';

/** Tells farmers on patchy signal that they are offline and that saved pages are being shown. */
export default function OfflineBanner({ text }) {
  const { t } = useLang();
  const [offline, setOffline] = useState(typeof navigator !== 'undefined' && navigator.onLine === false);
  useEffect(() => {
    const on = () => setOffline(false);
    const off = () => setOffline(true);
    window.addEventListener('online', on);
    window.addEventListener('offline', off);
    return () => { window.removeEventListener('online', on); window.removeEventListener('offline', off); };
  }, []);
  if (!offline) return null;
  return <div className="offline-banner" role="status">● {text || t('You are offline. Showing the pages you opened before; changes will need signal.')}</div>;
}
