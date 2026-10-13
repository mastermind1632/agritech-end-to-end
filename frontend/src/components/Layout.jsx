import { useEffect, useRef, useState } from 'react';
import Icon from './Icon';
import Avatar from './Avatar';

const nav = [
  ['dashboard', 'Overview', 'grid'],
  ['finance', 'My Finances', 'wallet'],
  ['market', 'Supplier Shop', 'market'],
  ['farmmarket', 'Farm Market', 'market'],
  ['groups', 'Group Orders', 'users'],
  ['ai', 'Buying Tips', 'spark'],
  ['notifications', 'Notifications', 'bell'],
  ['chat', 'Chat', 'chat'],
  ['supplier', 'Supplier Portal', 'market']
];

export default function Layout({ page, setPage, farmer, onLogout, children, noticeCount = 0 }) {
  const [open, setOpen] = useState(false);

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
    const label = (nav.find(([id]) => id === page) || [])[1] || (page === 'profile' ? 'My Profile' : 'AgriTech');
    document.title = label + ' | AgriTech';
  }, [page]);

  const go = (id) => { setPage(id); setOpen(false); window.scrollTo({ top: 0 }); };

  return (
    <div className="app-shell">
      {open && <div className="scrim" onClick={() => setOpen(false)} />}
      <aside className={`sidebar${open ? ' open' : ''}`}>
        <div className="brand">
          <div className="brand-mark"><Icon name="leaf" /></div>
          <div><b>AgriTech</b><span>COMMAND CENTER</span></div>
        </div>
        <div className="side-label">WORKSPACE</div>
        <nav>
          {nav.map(([id, label, icon]) => (
            <button key={id} className={page === id ? 'active' : ''} onClick={() => go(id)}>
              <Icon name={icon} />{label}
              {id === 'notifications' && noticeCount > 0 ? <em>{noticeCount}</em> : null}
            </button>
          ))}
        </nav>
        <div className="sidebar-bottom">
          <div className="mini-profile" style={{ cursor: 'pointer' }} onClick={() => go('profile')}>
            <Avatar id={farmer?.id} name={farmer?.name} size={35} />
            <div><b>{farmer?.name || 'Farmer'}</b><span>{farmer?.location || 'South Africa'}</span></div>
          </div>
          <button onClick={onLogout}><Icon name="logout" /> Sign out</button>
        </div>
      </aside>
      <main className="main">
        <header className="topbar">
          <div className="topbar-left">
            <button className="menu-btn" aria-label="Open menu" onClick={() => setOpen(true)}>☰</button>
            <span><span className="live-dot"></span> Online</span>
          </div>
          <div className="top-actions">
            <span className="location-pill">⌖ {farmer?.location || 'Your location'}</span>
            <Avatar id={farmer?.id} name={farmer?.name} size={32} />
          </div>
        </header>
        {children}
      </main>
    </div>
  );
}
