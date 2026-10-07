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
import useFarmerNotices from './hooks/useFarmerNotices';

const PAGES = { dashboard: Dashboard, finance: Finance, market: Marketplace, groups: Groups, ai: AI, notifications: Notifications, chat: Chat, profile: Profile, supplier: SupplierPortal };

function Shell({ farmer, onLogout }) {
  const [page, setPage] = useState('dashboard');
  const [initialOrderId, setInitialOrderId] = useState(null);
  const reviewOrder = id => { setInitialOrderId(id); setPage('groups'); };
  const notices = useFarmerNotices(farmer.id);
  const Page = PAGES[page] || SupplierPortal;
  return (
    <Layout page={page} setPage={setPage} farmer={farmer} onLogout={onLogout} noticeCount={notices.unread}>
      <Page farmer={farmer} setPage={setPage} notices={notices} reviewOrder={reviewOrder}
        initialOrderId={initialOrderId} onOrderOpened={() => setInitialOrderId(null)} />
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
  if (!farmer) return <Login onLogin={login} />;
  return <Shell farmer={farmer} onLogout={logout} />;
}
