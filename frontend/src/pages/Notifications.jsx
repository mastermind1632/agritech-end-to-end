import { useEffect, useState } from 'react';
import { notificationApi } from '../lib/api';
import { notificationsRead } from '../hooks/useNotifications';
import { onLive } from '../hooks/useLiveEvents';
import { SkeletonLines } from '../components/Skeleton';
import EmptyState from '../components/EmptyState';
import { useToast } from '../components/Toast';
import { dateTime, timeAgo } from '../lib/format';
import useLang from '../hooks/useLang';

const ICON = { ORDER: '⇄', DISPUTE: '⚖', GROUP: '♧', COMPLIANCE: '⏰', EXPORT: '✈', STATEMENT: '≣', SECURITY: '⚿', ACCOUNT: '⚿', VERIFICATION: '✓', LISTING: '▤', SYSTEM: 'i' };

export default function Notifications({ setPage }) {
  const toast = useToast();
  const { t } = useLang();
  const [items, setItems] = useState(null);
  const [page, setPageNo] = useState(0);
  const [hasMore, setHasMore] = useState(false);
  const [unreadOnly, setUnreadOnly] = useState(false);

  const load = async (p = 0) => {
    try {
      const res = await notificationApi.list(p, 30);
      setItems((prev) => (p === 0 ? res.items : [...(prev || []), ...res.items]));
      setHasMore(res.hasMore); setPageNo(p);
    } catch (e) { toast.error(e.message); setItems((x) => x || []); }
  };
  useEffect(() => {
    load(0);
    return onLive('notification', (n) => setItems((prev) => [n, ...(prev || []).filter((x) => x.id !== n.id)]));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const open = async (n) => {
    if (!n.read) {
      try { await notificationApi.read(n.id); } catch { /* still open it */ }
      setItems((prev) => prev.map((x) => (x.id === n.id ? { ...x, read: true } : x)));
      notificationsRead();
    }
    if (n.link) setPage(n.link);
  };
  const readAll = async () => {
    try { await notificationApi.readAll(); setItems((prev) => prev.map((x) => ({ ...x, read: true }))); notificationsRead(); }
    catch (e) { toast.error(e.message); }
  };
  const shown = (items || []).filter((n) => !unreadOnly || !n.read);
  const unread = (items || []).filter((n) => !n.read).length;

  return (
    <>
      <div className="page-head">
        <div>
          <p className="eyebrow">{t('UPDATES')}</p>
          <h1>{t('notifications')}</h1>
          <p className="muted">{t('Orders, disputes, group orders, compliance deadlines and export-rule changes. New ones arrive instantly. Choose SMS, WhatsApp or email in Settings.')}</p>
        </div>
        <div className="head-actions">
          <label className="check inline"><input type="checkbox" checked={unreadOnly} onChange={(e) => setUnreadOnly(e.target.checked)} /><span>{t('Unread only')}</span></label>
          <button className="secondary" onClick={readAll} disabled={!unread}>{t('markAllRead')}</button>
          <button className="link-btn" onClick={() => setPage('settings')}>{t('Alert settings')}</button>
        </div>
      </div>
      <div className="fm-body">
        <section className="panel">
          {!items ? <SkeletonLines lines={6} /> : !shown.length ? (
            <EmptyState icon="○" title={unreadOnly ? 'All caught up' : 'Nothing yet'}
                        text="When something needs your attention, such as a new order, a deadline or a reply, it appears here." />
          ) : shown.map((n) => (
            <button className={`list-row as-button notice-row ${n.read ? '' : 'unread'}`} key={n.id} onClick={() => open(n)}>
              <span className="round-icon">{ICON[n.category] || 'i'}</span>
              <div style={{ flex: 1 }}>
                <b>{t(n.title)}</b>
                {n.body && <p>{t(n.body)}</p>}
                <small className="muted" title={dateTime(n.createdAt)}>{timeAgo(n.createdAt)}</small>
              </div>
              {!n.read && <span className="tag">{t('NEW')}</span>}
            </button>
          ))}
          {hasMore && <div style={{ textAlign: 'center', marginTop: 12 }}><button className="secondary" onClick={() => load(page + 1)}>{t('Show older')}</button></div>}
        </section>
      </div>
    </>
  );
}
