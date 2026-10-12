import { useState } from 'react';
import Login from './pages/Login';
import Layout from './components/Layout';
import Dashboard from './pages/Dashboard';
import Finance from './pages/Finance';
import Marketplace from './pages/Marketplace';
import Groups from './pages/Groups';
import AI from './pages/AI';
import Notifications from './pages/Notifications';
import SupplierPortal from './pages/SupplierPortal';
import Chat from './pages/Chat';
import Profile from './pages/Profile';
import DeploymentNotice from './components/DeploymentNotice';
import Intelligence from './pages/Intelligence';
import useFarmerNotices from './hooks/useFarmerNotices';

const PAGES = { dashboard: Dashboard, finance: Finance, market: Marketplace, groups: Groups, ai: AI, intelligence: Intelligence, notifications: Notifications, chat: Chat, profile: Profile, supplier: SupplierPortal };

function Shell({ farmer, onLogout, onProductionSaved }) {
  const [page, setPage] = useState(farmer.setupProfile ? 'profile' : 'dashboard');
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
  const [farmer, setFarmer] = useState(() => {
    try { if (!localStorage.getItem('agritech_token')) return null; return JSON.parse(localStorage.getItem('agritech_farmer')); } catch { return null; }
  });
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
  return <Shell farmer={farmer} onLogout={logout} onProductionSaved={productionSaved} />;
}
