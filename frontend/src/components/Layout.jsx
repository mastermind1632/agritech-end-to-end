import { useEffect, useRef, useState } from 'react';
import Icon from './Icon';
import Avatar from './Avatar';
import OfflineBanner from './OfflineBanner';
import useLang from '../hooks/useLang';
import { LANGUAGES } from '../lib/i18n';
import ThemeToggle from './ThemeToggle';

// [page id, translation key, icon, roles allowed (empty = everyone)]
export const NAV = [
  ['FARM BUSINESS', 'farmBusiness', [
    ['dashboard', 'overview', 'grid', []],
    ['companion', 'companion', 'briefcase', ['FARMER', 'ADMIN']],
    ['statements', 'statements', 'receipt', ['FARMER', 'ADMIN']],
    ['finance', 'finance', 'wallet', ['FARMER', 'ADMIN']],
    ['export', 'export', 'globe', ['FARMER', 'ADMIN']],
    ['vault', 'vault', 'folder', []]
  ]],
  ['MARKET', 'marketplace', [
    ['farmmarket', 'farmmarket', 'basket', []],
    ['market', 'market', 'store', ['FARMER', 'ADMIN']],
    ['groups', 'groups', 'users', ['FARMER', 'SUPPLIER', 'ADMIN']],
    ['intelligence', 'intelligence', 'chart', ['FARMER', 'BUYER', 'ADMIN']],
    ['ai', 'ai', 'spark', ['FARMER', 'ADMIN']],
    ['supplier', 'supplier', 'market', ['SUPPLIER', 'ADMIN']]
  ]],
  ['COMMUNITY', 'community', [
    ['chat', 'chat', 'chat', []],
    ['notifications', 'notifications', 'bell', []],
    ['disputes', 'disputes', 'scale', []]
  ]],
  ['ACCOUNT', 'account', [
    ['settings', 'settings', 'gear', []],
    ['admin', 'admin', 'shield', ['ADMIN']]
  ]]
];

export function allowed(page, role) {
  if (page === 'profile') return true;
  for (const [, , items] of NAV) {
    const item = items.find(([id]) => id === page);
    if (item) return !item[3].length || item[3].includes(role || 'FARMER');
  }
  return false;
}

export const ROLE_LABEL = { FARMER: 'Farmer', BUYER: 'Buyer', SUPPLIER: 'Supplier', ADMIN: 'Administrator' };

export default function Layout({ page, setPage, farmer, onLogout, children, noticeCount = 0, disputeCount = 0 }) {
  const [open, setOpen] = useState(false);
  const { lang, setLang, t } = useLang();
  const role = farmer?.role || 'FARMER';

  // Close the phone menu with the Escape key, and lock page scrolling while it is open.
  useEffect(() => {
    const onKey = (e) => { if (e.key === 'Escape') setOpen(false); };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, []);
  useEffect(() => {
    document.body.style.overflow = open ? 'hidden' : '';
    return () => { document.body.style.overflow = ''; };
  }, [open]);

  // Browser history: every page change is a history entry, so the Back button walks
  // through the pages you visited instead of leaving the app.
  const setPageRef = useRef(setPage);
  setPageRef.current = setPage;
  useEffect(() => {
    const cur = window.history.state && window.history.state.page;
    if (cur === undefined || cur === null) window.history.replaceState({ page }, '');
    else if (cur !== page) window.history.pushState({ page }, '');
  }, [page]);
  useEffect(() => {
    const onPop = (e) => {
      const target = e.state && e.state.page;
      if (target) { setPageRef.current(target); setOpen(false); window.scrollTo({ top: 0 }); }
    };
    window.addEventListener('popstate', onPop);
    return () => window.removeEventListener('popstate', onPop);
  }, []);

  // Show the current page name in the browser tab.
  useEffect(() => {
    let label = page === 'profile' ? t('profile') : 'AgriTech';
    NAV.forEach(([, , items]) => items.forEach(([id, key]) => { if (id === page) label = t(key); }));
    document.title = label + ' | AgriTech';
  }, [page, lang]); // eslint-disable-line react-hooks/exhaustive-deps

  const go = (id) => { setPage(id); setOpen(false); window.scrollTo({ top: 0 }); };
  const count = (id) => (id === 'notifications' ? noticeCount : id === 'disputes' ? disputeCount : 0);

  return (
    <div className="app-shell">
      <a className="skip-link" href="#main">{t('Skip to content')}</a>
      {open && <div className="scrim" onClick={() => setOpen(false)} />}
      <aside className={`sidebar${open ? ' open' : ''}`} aria-label={t('Main menu')}>
        <div className="brand">
          <div className="brand-mark"><img src="/icons/icon.svg" alt="" /></div>
          <div><b>AgriTech</b><span>{t('FARM BUSINESS PLATFORM')}</span></div>
        </div>
        <nav>
          {NAV.map(([section, key, items]) => {
            const visible = items.filter(([, , , roles]) => !roles.length || roles.includes(role));
            if (!visible.length) return null;
            return (
              <div className="nav-section" key={section}>
                <div className="side-label">{t(key)}</div>
                {visible.map(([id, labelKey, icon]) => (
                  <button key={id} className={page === id ? 'active' : ''} onClick={() => go(id)} aria-current={page === id ? 'page' : undefined}>
                    <Icon name={icon} />{t(labelKey)}
                    {count(id) > 0 ? <em>{count(id) > 99 ? '99+' : count(id)}</em> : null}
                  </button>
                ))}
              </div>
            );
          })}
        </nav>
        <div className="sidebar-bottom">
          <div className="mini-profile" role="button" tabIndex={0} style={{ cursor: 'pointer' }} onClick={() => go('profile')}
               onKeyDown={(e) => { if (e.key === 'Enter') go('profile'); }}>
            <Avatar id={farmer?.id} name={farmer?.name} size={35} />
            <div><b>{farmer?.name || t('Farmer')}</b><span>{t(ROLE_LABEL[role] || role)} · {farmer?.location || t('South Africa')}</span></div>
          </div>
          <button onClick={onLogout}><Icon name="logout" /> {t('signOut')}</button>
        </div>
      </aside>
      <main className="main" id="main">
        <header className="topbar">
          <div className="topbar-left">
            <button className="menu-btn" aria-label={t('Open menu')} onClick={() => setOpen(true)}>☰</button>
            <span><span className="live-dot"></span> {t('online')}</span>
          </div>
          <div className="top-actions">
            <ThemeToggle />
            <label className="lang-select">
              <span className="sr-only">{t('language')}</span>
              <select value={lang} onChange={(e) => setLang(e.target.value)} aria-label={t('language')}>
                {LANGUAGES.map(([code, name]) => <option key={code} value={code}>{name}</option>)}
              </select>
            </label>
            <button className="bell" aria-label={`${t('notifications')} (${noticeCount})`} onClick={() => go('notifications')}>
              <svg viewBox="0 0 24 24" aria-hidden="true" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9" /><path d="M10.3 21a1.94 1.94 0 0 0 3.4 0" /></svg>{noticeCount > 0 && <em>{noticeCount > 99 ? '99+' : noticeCount}</em>}
            </button>
            <span className="location-pill">⌖ {farmer?.location || t('Your location')}</span>
            <button className="avatar-btn" onClick={() => go('profile')} aria-label={t('profile')}>
              <Avatar id={farmer?.id} name={farmer?.name} size={32} />
            </button>
          </div>
        </header>
        <OfflineBanner text={t('offline')} />
        <div key={page} className="page-anim">{children}</div>
      </main>
    </div>
  );
}
