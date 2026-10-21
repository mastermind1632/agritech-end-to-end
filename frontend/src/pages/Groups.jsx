import { useEffect, useState } from 'react';
import Modal from '../components/Modal';
import { groupApi, marketApi } from '../lib/api';
import { SkeletonCards } from '../components/Skeleton';
import EmptyState from '../components/EmptyState';
import Badge from '../components/Badge';
import Tabs from '../components/Tabs';
import { useToast } from '../components/Toast';
import { date, money } from '../lib/format';
import useLang from '../hooks/useLang';

const inDays = (n) => new Date(Date.now() + n * 86400000).toISOString().slice(0, 10);

function daysLeft(deadline) {
  if (!deadline) return null;
  return Math.ceil((new Date(deadline) - Date.now()) / 86400000);
}

export default function Groups({ farmer, initialOrderId, initialOrderQuantity, onOrderOpened }) {
  const toast = useToast();
  const { t } = useLang();
  const [tab, setTab] = useState('open');
  const [groups, setGroups] = useState(null);
  const [products, setProducts] = useState([]);
  const [selected, setSelected] = useState(null);
  const [qty, setQty] = useState(1);
  const [create, setCreate] = useState(false);
  const [form, setForm] = useState({ productId: '', targetQuantity: 100, discountRate: 10, deadline: inDays(14) });
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const load = () => groupApi.list(tab).then(setGroups).catch((e) => { toast.error(e.message); setGroups([]); });
  useEffect(() => { setGroups(null); load(); /* eslint-disable-next-line */ }, [tab]);
  useEffect(() => {
    marketApi.suppliers().then((ss) => Promise.all((ss || []).map((s) => marketApi.products(s.id).then((ps) => ps.map((p) => ({ ...p, supplierName: s.name }))))))
      .then((x) => setProducts(x.flat())).catch(() => {});
  }, []);
  useEffect(() => {
    if (!initialOrderId) return undefined;
    let active = true;
    groupApi.list('open').then((list) => {
      if (!active) return;
      const order = list.find((g) => g.id === initialOrderId);
      if (order) { setSelected(order); setQty(initialOrderQuantity || 1); } else toast.error(t('This group order is no longer open.'));
      onOrderOpened?.();
    }).catch((e) => { if (active) { toast.error(e.message); onOrderOpened?.(); } });
    return () => { active = false; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [initialOrderId]);

  const createOrder = async (e) => {
    e.preventDefault(); setError(''); setBusy(true);
    try {
      await groupApi.create({ ...form, targetQuantity: Number(form.targetQuantity), discountRate: Number(form.discountRate), deadline: form.deadline || null });
      setCreate(false); toast.success(t('Group order published. Other farmers can join now.')); load();
    } catch (err) { setError(err.message); }
    setBusy(false);
  };
  const join = async (e) => {
    e.preventDefault(); setError(''); setBusy(true);
    try {
      const g = await groupApi.join(selected.id, { farmerId: farmer.id, quantity: Number(qty) });
      setSelected(null);
      toast.success(g.status === 'completed' ? t('You filled the order! The target is reached and everyone has been told.') : t('You joined the group order.'));
      load();
    } catch (err) { setError(err.message); }
    setBusy(false);
  };
  const close = async (g) => {
    if (!window.confirm(t('Close this group order now? Members are notified and no one else can join.'))) return;
    try { await groupApi.close(g.id); toast.success(t('Group order closed.')); load(); } catch (e) { toast.error(e.message); }
  };

  const remaining = selected ? selected.targetQuantity - selected.currentQuantity : 1;

  return (
    <>
      <div className="page-head">
        <div>
          <p className="eyebrow">{t('COLLECTIVE BUYING')}</p>
          <h1>{t('Group orders')}</h1>
          <p className="muted">{t('Pool demand with other farmers and unlock supplier discounts. Joins are counted safely, so an order can never go over its target.')}</p>
        </div>
        <div className="head-actions">
          <Tabs value={tab} onChange={setTab} tabs={[['open', 'Open'], ['completed', 'Filled'], ['expired', 'Expired'], ['closed', 'Closed']]} />
          <button className="primary" onClick={() => { setError(''); setCreate(true); }}>{t('+ Create group order')}</button>
        </div>
      </div>
      <div className="fm-body">
        {!groups ? <SkeletonCards count={3} /> : !groups.length ? (
          <EmptyState icon="♧" title={tab === 'open' ? 'No open group orders' : tab === 'completed' ? 'No filled group orders' : tab === 'expired' ? 'No expired group orders' : 'No closed group orders'}
                      text="Start one for something you buy often, such as fertiliser or seed, and invite farmers nearby." action={tab === 'open' ? 'Create group order' : null} onAction={() => setCreate(true)} />
        ) : (
          <div className="group-grid flush">
            {groups.map((g) => {
              const p = products.find((x) => x.id === g.productId);
              const pct = Math.min(100, (g.currentQuantity / g.targetQuantity) * 100);
              const left = daysLeft(g.deadline);
              const mine = g.createdBy === farmer.id;
              return (
                <article className="group-card" key={g.id}>
                  <div className="group-top">
                    <span className="round-icon">♧</span>
                    <span className="discount">-{Number(g.discountRate)}%</span>
                  </div>
                  <p className="eyebrow">{p?.supplierName || t('BULK PURCHASE')}</p>
                  <h2>{p?.productName || t('Product order')}</h2>
                  <p className="muted">
                    {p && <>{t('{price} each, {group} in the group', { price: money(p.price), group: money(Number(p.price) * (1 - Number(g.discountRate) / 100)) })}<br /></>}
                    {t('Target {target} units · {have} committed · {left} to go', { target: g.targetQuantity, have: g.currentQuantity, left: g.targetQuantity - g.currentQuantity })}
                  </p>
                  <div className="progress"><span style={{ width: `${pct}%` }} /></div>
                  <div className="row-wrap" style={{ marginTop: 8 }}>
                    {g.status === 'open' && left != null && (
                      <Badge tone={left <= 2 ? 'bad' : left <= 7 ? 'warn' : 'neutral'}>
                        {left <= 0 ? t('Closes today') : t(left === 1 ? '{n} day left' : '{n} days left', { n: left })} · {date(g.deadline)}</Badge>
                    )}
                    {g.status !== 'open' && <Badge tone={g.status === 'completed' ? 'good' : 'neutral'}>{g.status === 'completed' ? t('Target reached') : t(g.status === 'expired' ? 'Expired' : g.status === 'closed' ? 'Closed' : g.status)}{g.closedAt ? ` · ${date(g.closedAt)}` : ''}</Badge>}
                    {mine && <Badge tone="info">You started this</Badge>}
                  </div>
                  <div className="group-foot">
                    <b>{t('{pct}% filled', { pct: Math.round(pct) })}</b>
                    <div className="row-wrap">
                      {g.status === 'open' && (mine || farmer.role === 'ADMIN') && <button className="link-btn" onClick={() => close(g)}>{t('Close')}</button>}
                      {g.status === 'open' && <button className="primary" onClick={() => { setError(''); setSelected(g); setQty(1); }}>{t('Join order →')}</button>}
                    </div>
                  </div>
                </article>
              );
            })}
          </div>
        )}
      </div>

      <Modal open={!!selected} title="Join group order" onClose={() => setSelected(null)}>
        <form onSubmit={join} className="modal-form">
          <p className="modal-copy">{t('Commit the quantity you want to buy.')} {t(remaining === 1 ? '{n} unit left before the target is reached.' : '{n} units left before the target is reached.', { n: remaining })}</p>
          <label>{t('Quantity')}<input type="number" min="1" max={remaining || 1} value={qty} onChange={(e) => setQty(e.target.value)} /></label>
          {error && <div className="error">{error}</div>}
          <button className="primary full" disabled={busy}>{t('Join group →')}</button>
        </form>
      </Modal>
      <Modal open={create} title="Create group order" onClose={() => setCreate(false)}>
        <form onSubmit={createOrder} className="modal-form">
          <label>{t('Product')}
            <select value={form.productId} onChange={(e) => setForm({ ...form, productId: e.target.value })} required>
              <option value="">{t('Choose product')}</option>
              {products.map((p) => <option key={p.id} value={p.id}>{p.productName} · {p.supplierName} · {money(p.price)}</option>)}
            </select>
          </label>
          <label>{t('Target quantity')}<input type="number" min="1" value={form.targetQuantity} onChange={(e) => setForm({ ...form, targetQuantity: e.target.value })} /></label>
          <label>{t('Discount rate (%)')}<input type="number" min="0" max="100" step="0.5" value={form.discountRate} onChange={(e) => setForm({ ...form, discountRate: e.target.value })} /></label>
          <label>{t('Deadline')}<input type="date" min={inDays(1)} value={form.deadline} onChange={(e) => setForm({ ...form, deadline: e.target.value })} /></label>
          <p className="muted">{t('If the target is not reached by the deadline, the order expires and every member is told.')}</p>
          {error && <div className="error">{error}</div>}
          <button className="primary full" disabled={busy}>{t('Publish group order →')}</button>
        </form>
      </Modal>
    </>
  );
}
