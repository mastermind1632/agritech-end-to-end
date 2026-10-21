import { useCallback, useEffect, useState } from 'react';
import Login from './pages/Login';
import Layout, { allowed } from './components/Layout';
import Dashboard from './pages/Dashboard';
import Finance from './pages/Finance';
import Marketplace from './pages/Marketplace';
import FarmMarket from './pages/FarmMarket';
import Groups from './pages/Groups';
import AI from './pages/AI';
import Intelligence from './pages/Intelligence';
import Notifications from './pages/Notifications';
import SupplierPortal from './pages/SupplierPortal';
import Chat from './pages/Chat';
import Profile from './pages/Profile';
import Companion from './pages/Companion';
import ExportReadiness from './pages/ExportReadiness';
import Statements from './pages/Statements';
import Documents from './pages/Documents';
import Disputes from './pages/Disputes';
import Admin from './pages/Admin';
import Settings from './pages/Settings';
import DeploymentNotice from './components/DeploymentNotice';
import { ToastProvider, useToast } from './components/Toast';
import useLiveEvents, { onLive } from './hooks/useLiveEvents';
import useNotifications from './hooks/useNotifications';
import { authApi, clearSession, disputeApi, FARMER_KEY, farmerApi, REFRESH_KEY, saveSession, TOKEN_KEY } from './lib/api';

const PAGES = {
  dashboard: Dashboard, finance: Finance, market: Marketplace, farmmarket: FarmMarket, groups: Groups, ai: AI,
  intelligence: Intelligence, notifications: Notifications, chat: Chat, profile: Profile, supplier: SupplierPortal,
  companion: Companion, export: ExportReadiness, statements: Statements, vault: Documents, disputes: Disputes,
  admin: Admin, settings: Settings
};

function savedFarmer() {
  try {
    if (!localStorage.getItem(TOKEN_KEY) && !localStorage.getItem(REFRESH_KEY)) return null;
    return JSON.parse(localStorage.getItem(FARMER_KEY));
  } catch { return null; }
}

function startPage() {
  const hash = window.location.hash.replace('#', '');
  return PAGES[hash] ? hash : 'dashboard';
}

function Shell({ farmer, onLogout, onProductionSaved, onFarmerChange }) {
  const [page, setPageRaw] = useState(startPage);
  const [initialOrder, setInitialOrder] = useState(null);
  const [disputeCount, setDisputeCount] = useState(0);
  const toast = useToast();
  const setPage = useCallback((p) => setPageRaw(allowed(p, farmer.role) ? p : 'dashboard'), [farmer.role]);
  const reviewOrder = (id, quantity = 1) => { setInitialOrder({ id, quantity }); setPage('groups'); };

  useLiveEvents(farmer.id);
  const { unread } = useNotifications(farmer.id, (n) => toast.info(n.body || '', {
    title: n.title, action: n.link ? { label: 'Open', onClick: () => setPage(n.link) } : undefined
  }));

  // Disputes waiting for my response (badge in the menu).
  const loadDisputes = useCallback(() => {
    disputeApi.mine().then((list) => setDisputeCount((list || []).filter((d) => d.myRole === 'AGAINST_ME' && d.status === 'OPEN').length)).catch(() => {});
  }, []);
  useEffect(() => {
    loadDisputes();
    const off = onLive('notification', (n) => { if (n?.category === 'DISPUTE') loadDisputes(); });
    return off;
  }, [loadDisputes]);

  useEffect(() => { if (!allowed(page, farmer.role)) setPageRaw('dashboard'); }, [farmer.role, page]);

  const Page = PAGES[page] || Dashboard;
  return (
    <Layout page={page} setPage={setPage} farmer={farmer} onLogout={onLogout} noticeCount={unread} disputeCount={disputeCount}>
      <DeploymentNotice />
      <Page farmer={farmer} setPage={setPage} reviewOrder={reviewOrder}
        onProductionSaved={onProductionSaved} onFarmerChange={onFarmerChange} onLogout={onLogout}
        initialOrderId={initialOrder?.id} initialOrderQuantity={initialOrder?.quantity}
        onOrderOpened={() => setInitialOrder(null)} />
    </Layout>
  );
}

export default function App() {
  const [farmer, setFarmer] = useState(savedFarmer);
  const [resetToken, setResetToken] = useState(() => new URLSearchParams(window.location.search).get('reset'));

  useEffect(() => {
    const syncAccount = (event) => {
      if (event.storageArea === localStorage && (event.key === null ||
          event.key === TOKEN_KEY || event.key === FARMER_KEY)) setFarmer(savedFarmer());
    };
    const onSession = (e) => { if (e.detail) setFarmer((f) => ({ ...f, ...e.detail })); };
    window.addEventListener('storage', syncAccount);
    window.addEventListener('agritech:session', onSession);
    return () => {
      window.removeEventListener('storage', syncAccount);
      window.removeEventListener('agritech:session', onSession);
    };
  }, []);

  // Refresh the profile once per visit, so a role change by an admin is picked up.
  useEffect(() => {
    if (!farmer?.id) return;
    farmerApi.me().then((me) => {
      setFarmer((f) => { const next = { ...f, ...me }; localStorage.setItem(FARMER_KEY, JSON.stringify(next)); return next; });
    }).catch(() => {});
  }, [farmer?.id]);

  const login = (data) => {
    saveSession(data);
    setFarmer(data.farmer);
    setResetToken(null);
    if (window.location.search.includes('reset=')) window.history.replaceState(null, '', window.location.pathname);
  };
  const logout = async () => {
    try { await authApi.logout(); } catch { /* signing out locally is enough */ }
    clearSession();
    setFarmer(null);
  };
  const changeFarmer = (patch) => setFarmer((f) => {
    const next = { ...f, ...patch };
    localStorage.setItem(FARMER_KEY, JSON.stringify(next));
    return next;
  });

  return (
    <ToastProvider>
      {!farmer || resetToken
        ? <><DeploymentNotice /><Login onLogin={login} resetToken={resetToken} onResetDone={() => { setResetToken(null); window.history.replaceState(null, '', window.location.pathname); }} /></>
        : <Shell key={farmer.id} farmer={farmer} onLogout={logout} onFarmerChange={changeFarmer}
                 onProductionSaved={() => changeFarmer({ setupProfile: false })} />}
    </ToastProvider>
  );
}
