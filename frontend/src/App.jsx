import { useEffect, useState } from 'react';
import Login from './pages/Login';
import Layout from './components/Layout';
import Dashboard from './pages/Dashboard';
import Finance from './pages/Finance';
import Marketplace from './pages/Marketplace';
import FarmMarket from './pages/FarmMarket';
import Groups from './pages/Groups';
import AI from './pages/AI';
import Notifications from './pages/Notifications';
import SupplierPortal from './pages/SupplierPortal';
import Chat from './pages/Chat';
import Profile from './pages/Profile';
import ExportReadiness from './pages/ExportReadiness';
import DeploymentNotice from './components/DeploymentNotice';
import useFarmerNotices from './hooks/useFarmerNotices';

const PAGES = { dashboard: Dashboard, finance: Finance, market: Marketplace, farmmarket: FarmMarket, groups: Groups, ai: AI, exports: ExportReadiness, notifications: Notifications, chat: Chat, profile: Profile, supplier: SupplierPortal };

function savedFarmer() {
  try { if (!localStorage.getItem('agritech_token')) return null; return JSON.parse(localStorage.getItem('agritech_farmer')); } catch { return null; }
}

function Shell({ farmer, onLogout, onProductionSaved }) {
  const [page, setPage] = useState('dashboard');
  const [initialOrder, setInitialOrder] = useState(null);
  const reviewOrder = (id, quantity = 1) => { setInitialOrder({ id, quantity }); setPage('groups'); };
  const notices = useFarmerNotices(farmer.id);
  const Page = PAGES[page] || SupplierPortal;
  return (
    <Layout page={page} setPage={setPage} farmer={farmer} onLogout={onLogout} noticeCount={notices.unread}>
      <DeploymentNotice />
      <Page farmer={farmer} setPage={setPage} notices={notices} reviewOrder={reviewOrder}
        onProductionSaved={onProductionSaved}
        initialOrderId={initialOrder?.id} initialOrderQuantity={initialOrder?.quantity}
        onOrderOpened={() => setInitialOrder(null)} />
    </Layout>
  );
}

export default function App() {
  const [farmer, setFarmer] = useState(savedFarmer);
  useEffect(() => {
    const syncAccount = (event) => {
      if (event.storageArea === localStorage && (event.key === null ||
          event.key === 'agritech_token' || event.key === 'agritech_farmer')) setFarmer(savedFarmer());
    };
    window.addEventListener('storage', syncAccount);
    return () => window.removeEventListener('storage', syncAccount);
  }, []);
  const login = (f) => {
    localStorage.setItem('agritech_farmer', JSON.stringify(f.farmer || f));
    if (f.token) localStorage.setItem('agritech_token', f.token);
    setFarmer(f.farmer || f);
  };
  const logout = () => {
    localStorage.removeItem('agritech_farmer'); localStorage.removeItem('agritech_token'); setFarmer(null);
  };
  if (!farmer) return <><DeploymentNotice /><Login onLogin={login} /></>;
  const productionSaved = () => {
    const updated = { ...farmer, setupProfile: false };
    localStorage.setItem('agritech_farmer', JSON.stringify(updated));
    setFarmer(updated);
  };
  return <Shell key={farmer.id} farmer={farmer} onLogout={logout} onProductionSaved={productionSaved} />;
}
