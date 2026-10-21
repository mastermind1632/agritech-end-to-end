import { useEffect, useMemo, useState } from 'react';
import Modal from '../components/Modal';
import { companionApi, financeApi } from '../lib/api';
import { BarChart, BarList } from '../components/Charts';
import { SkeletonLines } from '../components/Skeleton';
import EmptyState from '../components/EmptyState';
import Badge from '../components/Badge';
import { useToast } from '../components/Toast';
import { date, money } from '../lib/format';
import useLang from '../hooks/useLang';

const CATEGORIES = ['Seeds & seedlings', 'Fertiliser', 'Agrochemicals', 'Fuel', 'Labour & wages', 'Equipment purchase', 'Repairs & maintenance', 'Electricity & water', 'Transport & logistics', 'Rent & leases', 'Loan repayments', 'Insurance', 'Bank fees', 'Farm supplies (co-op)', 'Other farm expense'];
const SOURCE = { STATEMENT: 'Bank statement', MARKETPLACE: 'Farm Market' };

export default function Finance({ farmer, setPage }) {
  const toast = useToast();
  const { t } = useLang();
  const [summary, setSummary] = useState(null);
  const [expenses, setExpenses] = useState([]);
  const [income, setIncome] = useState([]);
  const [monthly, setMonthly] = useState([]);
  const [crops, setCrops] = useState([]);
  const [modal, setModal] = useState(null);
  const [form, setForm] = useState({ item: '', category: 'Fertiliser', crop: '', amount: '', date: new Date().toISOString().slice(0, 10) });
  const [error, setError] = useState('');
  const [filter, setFilter] = useState('all');
  const [showAll, setShowAll] = useState(false);

  const load = () => Promise.all([financeApi.summary(farmer.id), financeApi.expenses(farmer.id), financeApi.income(farmer.id), financeApi.monthly(12)])
    .then(([s, e, i, m]) => { setSummary(s); setExpenses(e || []); setIncome(i || []); setMonthly(m || []); })
    .catch((e) => { toast.error(e.message); setSummary({}); });
  useEffect(() => {
    load();
    if (farmer.role === 'FARMER') companionApi.plan().then((p) => setCrops([...new Set((p.crops || []).map((c) => c.crop))])).catch(() => {});
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [farmer.id]);

  const submit = async (e) => {
    e.preventDefault(); setError('');
    try {
      const body = { item: form.item, farmerId: farmer.id, amount: Number(form.amount), ...(form.date ? { date: form.date } : {}),
        ...(modal === 'expense' ? { category: form.category, crop: form.crop || null } : {}) };
      if (modal === 'expense') await financeApi.addExpense(body); else await financeApi.addIncome(body);
      setModal(null); setForm({ ...form, item: '', amount: '' }); toast.success(t('Saved.')); load();
    } catch (err) { setError(err.message); }
  };
  const remove = async (r) => {
    if (!window.confirm(t('Delete "{item}"?', { item: r.item }))) return;
    try {
      if (r.type === 'Expense') await financeApi.deleteExpense(r.id, farmer.id); else await financeApi.deleteIncome(r.id, farmer.id);
      load();
    } catch (e) { toast.error(e.message); }
  };

  const all = useMemo(() => [...expenses.map((x) => ({ ...x, type: 'Expense' })), ...income.map((x) => ({ ...x, type: 'Income' }))]
    .sort((a, b) => String(b.date).localeCompare(String(a.date))), [expenses, income]);
  const rows = all.filter((r) => filter === 'all' || (filter === 'in' ? r.type === 'Income' : r.type === 'Expense'));
  const byCategory = useMemo(() => {
    const m = {};
    const since = new Date(); since.setMonth(since.getMonth() - 12);
    expenses.filter((e) => new Date(e.date) >= since).forEach((e) => { const c = e.category || 'Other'; m[c] = (m[c] || 0) + Number(e.amount); });
    return Object.entries(m).map(([label, amount]) => ({ label, amount })).sort((a, b) => b.amount - a.amount).slice(0, 8);
  }, [expenses]);
  const hasMonthly = monthly.some((m) => Number(m.income) || Number(m.expenses));
  const totalIn = Number(summary?.totalIncome || 0);
  const totalOut = Number(summary?.totalExpenses || 0);

  return (
    <>
      <div className="page-head">
        <div>
          <p className="eyebrow">{t('YOUR MONEY')}</p>
          <h1>{t('Farm finances')}</h1>
          <p className="muted">{t('Know where money is going and what your farm is earning. Farm Market sales are added automatically, and bank statements can be imported in Expense Analysis.')}</p>
        </div>
        <div className="head-actions">
          {all.length > 0 && <button className="secondary" onClick={() => financeApi.exportXlsx().catch((e) => toast.error(e.message))}>{t('Excel')}</button>}
          {farmer.role === 'FARMER' && <button className="secondary" onClick={() => setPage('statements')}>{t('Import bank statement')}</button>}
          <button className="secondary" onClick={() => setModal('expense')}>{t('− Expense')}</button>
          <button className="primary" onClick={() => setModal('income')}>{t('+ Income')}</button>
        </div>
      </div>
      <section className="stats">
        <div className="stat-card"><span>{t('Income')}</span><strong>{summary ? money(totalIn) : '…'}</strong><small className="positive">{t('All your sales and income')}</small></div>
        <div className="stat-card"><span>{t('Expenses')}</span><strong>{summary ? money(totalOut) : '…'}</strong><small>{t('All your farm costs')}</small></div>
        <div className="stat-card"><span>{t('Money left')}</span><strong className={totalIn - totalOut < 0 ? 'negative' : ''}>{summary ? money(totalIn - totalOut) : '…'}</strong><small>{t('Income minus expenses')}</small></div>
      </section>
      {hasMonthly && (
        <div className="dashboard-grid">
          <section className="panel">
            <div className="panel-head"><div><p className="eyebrow">{t('LAST 12 MONTHS')}</p><h2>{t('Income and expenses')}</h2></div></div>
            <BarChart rows={monthly} series={[{ key: 'income', label: 'Income', color: '#3d9d6c' }, { key: 'expenses', label: 'Expenses', color: '#c98a3a' }]} />
          </section>
          <section className="panel">
            <div className="panel-head"><div><p className="eyebrow">{t('LAST 12 MONTHS')}</p><h2>{t('Biggest costs')}</h2></div></div>
            {byCategory.length ? <BarList items={byCategory} /> : <p className="muted">{t('No expenses yet.')}</p>}
          </section>
        </div>
      )}
      <section className="panel table-panel">
        <div className="panel-head">
          <div><p className="eyebrow">{t('HISTORY')}</p><h2>{t('Transactions')}</h2></div>
          <div className="segmented">
            {[['all', 'All'], ['in', 'Income'], ['out', 'Expenses']].map(([k, l]) => <button key={k} className={filter === k ? 'on' : ''} onClick={() => setFilter(k)}>{t(l)}</button>)}
          </div>
        </div>
        {!summary ? <SkeletonLines lines={6} /> : rows.length ? (
          <div className="table-wrap">
            <table>
              <thead><tr><th>{t('Date')}</th><th>{t('Item')}</th><th>{t('Category')}</th><th>{t('Type')}</th><th className="right">{t('Amount')}</th><th /></tr></thead>
              <tbody>
                {(showAll ? rows : rows.slice(0, 15)).map((r) => (
                  <tr key={`${r.type}-${r.id}`}>
                    <td>{date(r.date)}</td>
                    <td><b>{r.item}</b>{r.source && SOURCE[r.source] && <> <Badge tone="info">{SOURCE[r.source]}</Badge></>}</td>
                    <td>{r.category ? t(r.category) : '—'}{r.crop && <small className="muted"> · {t(r.crop)}</small>}</td>
                    <td><span className={`status ${r.type === 'Income' ? 'success' : 'danger'}`}>{t(r.type)}</span></td>
                    <td className="right"><b>{r.type === 'Income' ? '+' : '−'} {money(r.amount)}</b></td>
                    <td>{!r.source && <button className="link-btn" aria-label={t('Delete "{item}"?', { item: r.item })} onClick={() => remove(r)}>{t('Delete')}</button>}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            {rows.length > 15 && <div style={{ textAlign: 'center', marginTop: 10 }}><button className="link-btn" onClick={() => setShowAll(!showAll)}>{showAll ? t('Show fewer') : t('Show all {n}', { n: rows.length })}</button></div>}
          </div>
        ) : (
          <EmptyState icon="▣" title="No transactions yet" text="Add your first sale or expense, or import a bank statement to fill this in automatically."
                      action="Add income" onAction={() => setModal('income')} secondary={farmer.role === 'FARMER' ? 'Import a statement' : null} onSecondary={() => setPage('statements')} />
        )}
      </section>
      <Modal open={!!modal} title={modal === 'expense' ? 'Record an expense' : 'Record income'} onClose={() => setModal(null)}>
        <form onSubmit={submit} className="modal-form">
          <label>{t('Item')}<input value={form.item} onChange={(e) => setForm({ ...form, item: e.target.value })} placeholder={modal === 'expense' ? t('e.g. LAN fertiliser 10 bags') : t('e.g. Tomatoes to Spar')} required /></label>
          {modal === 'expense' && (
            <>
              <label>{t('Category')}<select value={form.category} onChange={(e) => setForm({ ...form, category: e.target.value })}>{CATEGORIES.map((c) => <option key={c} value={c}>{t(c)}</option>)}</select></label>
              <label>{t('Crop (optional)')}
                <input list="finance-crops" value={form.crop} onChange={(e) => setForm({ ...form, crop: e.target.value })} placeholder={t('e.g. Tomatoes')} />
                <datalist id="finance-crops">{crops.map((c) => <option key={c} value={c} />)}</datalist>
              </label>
            </>
          )}
          <label>{t('Amount (ZAR)')}<input type="number" min="0" step="0.01" value={form.amount} onChange={(e) => setForm({ ...form, amount: e.target.value })} required /></label>
          <label>{t('Date')}<input type="date" value={form.date} onChange={(e) => setForm({ ...form, date: e.target.value })} /></label>
          {error && <div className="error">{error}</div>}
          <button className="primary full">{t('Save transaction →')}</button>
        </form>
      </Modal>
    </>
  );
}
