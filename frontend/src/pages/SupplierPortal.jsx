import { useEffect, useState } from 'react';
import Modal from '../components/Modal';
import useLive from '../hooks/useLive';
import { api, marketApi } from '../lib/api';

const emptySupplier = { name: '', location: '', contact: '' };
const emptyProduct = { productName: '', price: '' };

export default function SupplierPortal() {
  const [suppliers, setSuppliers] = useState([]);
  const [selected, setSelected] = useState('');
  const [loadError, setLoadError] = useState('');
  const [supplierModal, setSupplierModal] = useState(null);
  const [productModal, setProductModal] = useState(null);
  const [sForm, setSForm] = useState(emptySupplier);
  const [pForm, setPForm] = useState(emptyProduct);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [flash, setFlash] = useState('');

  const loadSuppliers = async (pick) => {
    try {
      const list = (await marketApi.suppliers()) || [];
      setSuppliers(list);
      setSelected((cur) => pick ?? (list.some((s) => s.id === cur) ? cur : list[0]?.id || ''));
    } catch (e) { setLoadError(e.message); }
  };
  useEffect(() => { loadSuppliers(); }, []);

  const { data, error: dataError, refresh } = useLive(async () => {
    const [products, notifications, count] = await Promise.all([
      marketApi.products(selected), api(`/notifications/supplier/${selected}`), api(`/notifications/supplier/${selected}/unread-count`)
    ]);
    return { products: products || [], notifications: notifications || [], count: count || 0 };
  }, [selected], { enabled: !!selected, interval: 10000 });
  const { products = [], notifications = [], count = 0 } = data || {};
  const current = suppliers.find((s) => s.id === selected);

  const note = (m) => { setFlash(m); setTimeout(() => setFlash(''), 3500); };
  const run = async (fn, done) => {
    setError(''); setBusy(true);
    try { await fn(); done(); } catch (e) { setError(e.message); } finally { setBusy(false); }
  };

  const openNewSupplier = () => { setSForm(emptySupplier); setError(''); setSupplierModal('new'); };
  const openEditSupplier = () => { setSForm({ name: current.name || '', location: current.location || '', contact: current.contact || '' }); setError(''); setSupplierModal('edit'); };
  const saveSupplier = (e) => {
    e.preventDefault();
    run(async () => {
      if (supplierModal === 'new') { const created = await marketApi.createSupplier(sForm); await loadSuppliers(created.id); note('Supplier saved to the database.'); }
      else { await marketApi.updateSupplier(selected, sForm); await loadSuppliers(selected); note('Supplier updated.'); }
    }, () => setSupplierModal(null));
  };
  const removeSupplier = async () => {
    if (!window.confirm(`Delete supplier "${current.name}"? Its products will no longer be reachable.`)) return;
    try { await marketApi.deleteSupplier(selected); await loadSuppliers(''); note('Supplier deleted.'); } catch (e) { setLoadError(e.message); }
  };

  const openNewProduct = () => { setPForm(emptyProduct); setError(''); setProductModal('new'); };
  const openEditProduct = (p) => { setPForm({ productName: p.productName, price: String(p.price) }); setError(''); setProductModal(p); };
  const saveProduct = (e) => {
    e.preventDefault();
    const body = { productName: pForm.productName, price: Number(pForm.price) };
    run(async () => {
      if (productModal === 'new') { await marketApi.addProduct(selected, body); note('Product saved to the database.'); }
      else { await marketApi.updateProduct(productModal.id, body); note('Product updated.'); }
      await refresh();
    }, () => setProductModal(null));
  };
  const removeProduct = async (p) => {
    if (!window.confirm(`Delete "${p.productName}"?`)) return;
    try { await marketApi.deleteProduct(p.id); note('Product deleted.'); refresh(); } catch (e) { setLoadError(e.message); }
  };
  const markRead = async (id) => { try { await api(`/notifications/${id}/read`, { method: 'PUT' }); refresh(); } catch (e) { setLoadError(e.message); } };

  return (
    <>
      <div className="page-head">
        <div><p className="eyebrow">SUPPLIER WORKSPACE</p><h1>Supplier portal</h1><p className="muted">Add suppliers and products here. Everything is saved to PostgreSQL through the API and shows up in the Marketplace and Group Orders.</p></div>
        <div className="head-actions">
          {suppliers.length > 0 && <select className="filter" value={selected} onChange={(e) => setSelected(e.target.value)}>{suppliers.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}</select>}
          <button className="primary" onClick={openNewSupplier}>+ Add supplier</button>
        </div>
      </div>
      {flash && <div className="notice">{flash}</div>}
      {(dataError || loadError) && <div className="error">{loadError || dataError}</div>}

      {!suppliers.length ? (
        <div className="empty"><span>▤</span><b>No suppliers yet</b><p>Add your first supplier, then list products for it.</p><button className="primary" onClick={openNewSupplier}>+ Add supplier</button></div>
      ) : (
        <>
          <section className="stats">
            <div className="stat-card"><span>Listed products</span><strong>{products.length}</strong><small>Active catalogue</small></div>
            <div className="stat-card"><span>Unread alerts</span><strong>{count}</strong><small className="positive">Group-order activity</small></div>
            <div className="stat-card"><span>Location</span><strong>{current?.location || '—'}</strong><small>{current?.contact || 'No contact set'}</small></div>
          </section>
          <div className="head-actions" style={{ padding: '0 48px 16px' }}>
            <button className="secondary" onClick={openEditSupplier}>Edit supplier</button>
            <button className="secondary" onClick={removeSupplier}>Delete supplier</button>
          </div>
          <div className="dashboard-grid">
            <section className="panel">
              <div className="panel-head"><div><p className="eyebrow">CATALOGUE</p><h2>Products</h2></div><button className="primary" onClick={openNewProduct}>+ Add product</button></div>
              {products.map((p) => (
                <div className="list-row" key={p.id}><span className="round-icon">❧</span>
                  <div style={{ flex: 1 }}><b>{p.productName}</b><p>Listed at R {Number(p.price).toFixed(2)}</p></div>
                  <button className="text-button" onClick={() => openEditProduct(p)}>Edit</button>
                  <button className="text-button" onClick={() => removeProduct(p)}>Delete</button>
                </div>))}
              {!products.length && <div className="empty"><span>▤</span><b>No products listed</b><p>Click "Add product" to list your first item.</p></div>}
            </section>
            <section className="panel">
              <div className="panel-head"><div><p className="eyebrow">INBOX</p><h2>Group-order alerts</h2></div></div>
              {notifications.slice(0, 10).map((n) => (
                <div className="list-row" key={n.id}><span className="round-icon">◔</span>
                  <div style={{ flex: 1 }}><b>{n.read ? 'Read' : 'New alert'}</b><p>{n.message}</p></div>
                  {!n.read && <button className="text-button" onClick={() => markRead(n.id)}>Mark read</button>}
                </div>))}
              {!notifications.length && <div className="empty"><span>◔</span><b>No alerts</b><p>Alerts appear when farmers create or join group orders for this supplier's products.</p></div>}
            </section>
          </div>
        </>
      )}

      <Modal open={!!supplierModal} title={supplierModal === 'edit' ? 'Edit supplier' : 'Add supplier'} onClose={() => setSupplierModal(null)}>
        <form onSubmit={saveSupplier} className="modal-form">
          <label>Name<input value={sForm.name} onChange={(e) => setSForm({ ...sForm, name: e.target.value })} placeholder="Limpopo Agri Co-op" required /></label>
          <label>Location<input value={sForm.location} onChange={(e) => setSForm({ ...sForm, location: e.target.value })} placeholder="Polokwane" /></label>
          <label>Contact<input value={sForm.contact} onChange={(e) => setSForm({ ...sForm, contact: e.target.value })} placeholder="015 123 4567" /></label>
          {error && <div className="error" style={{ margin: 0 }}>{error}</div>}
          <button className="primary full" disabled={busy}>{busy ? 'Saving…' : 'Save supplier →'}</button>
        </form>
      </Modal>
      <Modal open={!!productModal} title={productModal === 'new' ? 'Add product' : 'Edit product'} onClose={() => setProductModal(null)}>
        <form onSubmit={saveProduct} className="modal-form">
          <label>Product name<input value={pForm.productName} onChange={(e) => setPForm({ ...pForm, productName: e.target.value })} placeholder="50kg NPK fertilizer" required /></label>
          <label>Price (ZAR)<input type="number" min="0" step="0.01" inputMode="decimal" value={pForm.price} onChange={(e) => setPForm({ ...pForm, price: e.target.value })} required /></label>
          {error && <div className="error" style={{ margin: 0 }}>{error}</div>}
          <button className="primary full" disabled={busy}>{busy ? 'Saving…' : 'Save product →'}</button>
        </form>
      </Modal>
    </>
  );
}