import { useEffect, useRef, useState } from 'react';
import { intelligenceApi } from '../lib/api';
import '../market-intelligence.css';

const money = n => n == null ? 'Not stated' : new Intl.NumberFormat('en-ZA', { style: 'currency', currency: 'ZAR' }).format(n);
const today = () => new Date().toLocaleDateString('en-CA');
const INITIAL = { kind: 'offer', crop: '', province: '', district: '', latitude: '', longitude: '', quantityKg: '', pricePerKg: '', availableFrom: today(), availableUntil: today(), displayName: '', publicContact: '' };

function ForecastQuality({ forecast }) {
  const error = forecast.validation?.methods?.[forecast.method];
  return <>
    {forecast.dataCoverage && <p className="muted">{forecast.dataCoverage.completedObservedMonths} completed months of observations; {forecast.dataCoverage.missingMonths} missing months.</p>}
    {!!error?.samples && <p className="muted">Historical forecast error: {Number(error.maeKg).toLocaleString('en-ZA', { maximumFractionDigits: 1 })} kg on average across {error.samples} comparisons.
      {error.wape != null && <> Volume-weighted error: {(error.wape * 100).toLocaleString('en-ZA', { maximumFractionDigits: 1 })}%.</>}</p>}
  </>;
}

export default function Intelligence({ setPage }) {
  const [tab, setTab] = useState('matches');
  const [options, setOptions] = useState(null);
  const [recommendations, setRecommendations] = useState(null);
  const [rows, setRows] = useState([]);
  const [mine, setMine] = useState([]);
  const [insights, setInsights] = useState(null);
  const [filter, setFilter] = useState({ kind: 'offer', crop: '', province: '' });
  const [form, setForm] = useState(INITIAL);
  const [error, setError] = useState('');
  const [actionError, setActionError] = useState('');
  const [optionsError, setOptionsError] = useState('');
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [status, setStatus] = useState('');
  const [needsProfile, setNeedsProfile] = useState(false);
  const mounted = useRef(false);
  const viewVersion = useRef(0);
  const currentView = version => mounted.current && viewVersion.current === version;
  useEffect(() => {
    let active = true;
    mounted.current = true;
    intelligenceApi.options().then(o => { if (active) setOptions(o); }).catch(e => { if (active) setOptionsError(e.message); });
    intelligenceApi.profile().then(p => {
      if (!active || !p.profile) return;
      setForm(f => ({ ...f, ...Object.fromEntries(['crop', 'province', 'district', 'latitude', 'longitude']
        .filter(key => f[key] === '').map(key => [key, p.profile[key]])) }));
    }).catch(() => {});
    return () => { active = false; mounted.current = false; };
  }, []);
  useEffect(() => {
    const version = ++viewVersion.current;
    setLoading(true); setError(''); setActionError(''); setNeedsProfile(false);
    const request = tab === 'matches' ? intelligenceApi.recommendations() : tab === 'demand' ? intelligenceApi.demand() : Promise.all([
      ...['offer', 'demand'].map(kind => intelligenceApi.records({ mine: true, kind })),
      intelligenceApi.records(Object.fromEntries(Object.entries(filter).filter(([, v]) => v !== '')))
    ]);
    request.then(result => {
      if (!currentView(version)) return;
      if (tab === 'matches') setRecommendations(result);
      else if (tab === 'demand') setInsights(result);
      else { setMine(result.slice(0, 2).flatMap(r => r.records)); setRows(result[2].records); }
    }).catch(e => { if (currentView(version)) { setError(e.message); setNeedsProfile(e.status === 409); } }).finally(() => { if (currentView(version)) setLoading(false); });
    return () => { if (viewVersion.current === version) viewVersion.current += 1; };
  }, [tab]);
  const search = async e => {
    e.preventDefault();
    if (busy || loading) return;
    const version = viewVersion.current;
    setBusy(true); setLoading(true); setError(''); setActionError(''); setStatus('');
    try {
      const r = await intelligenceApi.records(Object.fromEntries(Object.entries(filter).filter(([, v]) => v !== '')));
      if (currentView(version)) { setRows(r.records); setStatus(`${r.resultCount} matching listings${r.limited ? '; showing the latest 100' : ''}.`); }
    }
    catch (err) { if (currentView(version)) setError(err.message); }
    finally { if (mounted.current) setBusy(false); if (currentView(version)) setLoading(false); }
  };
  const publish = async e => {
    e.preventDefault();
    if (busy || loading) return;
    const version = viewVersion.current;
    setBusy(true); setActionError(''); setStatus('');
    try {
      const body = { ...form, latitude: Number(form.latitude), longitude: Number(form.longitude), quantityKg: Number(form.quantityKg), pricePerKg: form.pricePerKg === '' ? null : Number(form.pricePerKg) };
      const r = await intelligenceApi.addRecord(body);
      if (mounted.current) setMine(m => [r, ...m.filter(existing => existing.id !== r.id)]);
      if (currentView(version)) setStatus('Listing published.');
    } catch (err) { if (currentView(version)) setActionError(err.message); }
    finally { if (mounted.current) setBusy(false); }
  };
  const remove = async id => {
    if (busy || loading) return;
    if (!window.confirm('Remove this listing?')) return;
    const version = viewVersion.current;
    setBusy(true); setActionError(''); setStatus('');
    try {
      await intelligenceApi.deleteRecord(id);
      if (mounted.current) { setMine(m => m.filter(r => r.id !== id)); setRows(r => r.filter(row => row.id !== id)); }
      if (currentView(version)) setStatus('Listing removed.');
    }
    catch (err) { if (currentView(version)) setActionError(err.message); }
    finally { if (mounted.current) setBusy(false); }
  };
  const engage = async recordId => {
    if (tab !== 'matches' || !recommendations?.impressionId) return;
    const version = viewVersion.current;
    try { await intelligenceApi.feedback({ impressionId: recommendations.impressionId, recordId, action: 'click' }); }
    catch (err) { if (currentView(version)) setActionError(err.message); }
  };
  const record = (r, own = false) => <article className="mi-listing" key={r.id}>
    <div><span className="tag">{r.kind === 'demand' ? 'BUYER REQUEST' : 'PRODUCE OFFER'}</span><h3>{r.crop} <small>{r.quantityKg.toLocaleString()} kg</small></h3><p>{r.displayName} | {r.district}, {r.province}</p><p>{r.availableFrom} to {r.availableUntil} | {money(r.pricePerKg)} / kg</p>{r.distanceKm != null && <p>{r.distanceKm} km away</p>}{r.reasons && <ul>{r.reasons.map(reason => <li key={reason}>{reason}</li>)}</ul>}</div>
    <div className="mi-listing-actions">{r.publicContact && <button className="secondary" onClick={() => { engage(r.id); window.alert(`Public contact: ${r.publicContact}`); }}>View contact</button>}{own && <button className="secondary" disabled={busy || loading} onClick={() => remove(r.id)}>Remove listing</button>}</div>
  </article>;
  return <>
    <div className="page-head"><div><p className="eyebrow">MARKET INTELLIGENCE</p><h1>Produce markets</h1></div><button className="secondary" onClick={() => setPage('profile')}>Production profile</button></div>
    <section className="mi-workspace">
      <div className="mi-tabs" role="tablist" aria-label="Produce markets">{[['matches', 'My opportunities'], ['market', 'Listings'], ['demand', 'Demand insights']].map(([key, label]) => <button key={key} role="tab" aria-selected={tab === key} onClick={() => { setTab(key); setStatus(''); }} className={tab === key ? 'active' : ''}>{label}</button>)}</div>
      {error && <p className="error" role="alert">{error}</p>}{actionError && <p className="error" role="alert">{actionError}</p>}{tab === 'market' && optionsError && <p className="error" role="alert">{optionsError}</p>}{status && <p className="notice" role="status">{status}</p>}
      {needsProfile && <button className="primary" onClick={() => setPage('profile')}>Complete production profile</button>}
      {loading && <p aria-live="polite">Loading market data...</p>}
      {tab === 'matches' && recommendations && !loading && !error && <>
        <div className="mi-segment"><span className="tag">{recommendations.segment.method === 'rules' ? 'RULE-BASED SEGMENT' : 'ML SEGMENT'}</span><h2>{recommendations.segment.name}</h2><p className="muted">{recommendations.segment.reviewStatus}</p></div>
        <h2>Buyer matches</h2>{recommendations.recommendations.length ? recommendations.recommendations.map(r => record(r)) : <p className="mi-empty">No buyer requests match your crop, harvest month, delivery radius and capacity yet.</p>}
        <p className="muted">{recommendations.notice}</p>
        {recommendations.services.length > 0 && <><h2>Service opportunities</h2><ul className="mi-services">{recommendations.services.map(s => <li key={s.service}><strong>{({ business_companion: 'Business registration support', export_readiness: 'Export readiness', expense_insights: 'Expense insights' })[s.service] || s.service.replaceAll('_', ' ')}</strong><p>{s.reason}</p></li>)}</ul></>}
      </>}
      {tab === 'market' && options && <>
        <form className="mi-search" onSubmit={search}><label>Listing type<select value={filter.kind} onChange={e => setFilter({ ...filter, kind: e.target.value })}><option value="offer">Produce offers</option><option value="demand">Buyer requests</option></select></label><label>Crop<select value={filter.crop} onChange={e => setFilter({ ...filter, crop: e.target.value })}><option value="">All crops</option>{options.crops.map(c => <option key={c}>{c}</option>)}</select></label><label>Province<select value={filter.province} onChange={e => setFilter({ ...filter, province: e.target.value })}><option value="">All provinces</option>{options.provinces.map(p => <option key={p}>{p}</option>)}</select></label><button className="primary" disabled={busy || loading}>Search</button></form>
        {!loading && !error && <div>{rows.length ? rows.map(r => record(r)) : <p className="mi-empty">No recorded listings match these filters.</p>}</div>}
        <h2>Publish a listing</h2>
        <form className="production-form" onSubmit={publish}>
          <label>Listing type<select value={form.kind} onChange={e => setForm({ ...form, kind: e.target.value })}><option value="offer">Produce offer</option><option value="demand">Buyer request</option></select></label>
          <label>Crop<select required value={form.crop} onChange={e => setForm({ ...form, crop: e.target.value })}><option value="">Select crop</option>{options.crops.map(c => <option key={c}>{c}</option>)}</select></label>
          <label>Province<select required value={form.province} onChange={e => setForm({ ...form, province: e.target.value })}><option value="">Select province</option>{options.provinces.map(p => <option key={p}>{p}</option>)}</select></label>
          {['district', 'displayName', 'publicContact'].map(k => <label key={k}>{({ district: 'District', displayName: 'Public business / display name', publicContact: 'Public contact (optional)' })[k]}<input maxLength={100} minLength={k === 'publicContact' ? undefined : 2} required={k !== 'publicContact'} value={form[k]} onChange={e => setForm({ ...form, [k]: e.target.value })} /></label>)}
          {[['quantityKg', 'Quantity (kg)', 0.001, 1000000000], ['pricePerKg', 'Offer price / buyer budget (ZAR per kg)', 0, 1000000], ['latitude', 'Latitude', -35, -22], ['longitude', 'Longitude', 16, 33]].map(([k, label, min, max]) => <label key={k}>{label}<input type="number" min={min} max={max} step="any" required={k !== 'pricePerKg' || form.kind === 'offer'} value={form[k]} onChange={e => setForm({ ...form, [k]: e.target.value })} /></label>)}
          {['availableFrom', 'availableUntil'].map(k => <label key={k}>{k === 'availableFrom' ? 'Available from' : 'Available until'}<input type="date" required min={k === 'availableUntil' ? form.availableFrom : undefined} value={form[k]} onChange={e => setForm({ ...form, [k]: e.target.value })} /></label>)}
          <p className="mi-disclosure">The listing name, contact, district and province will be public to signed-in users. Exact coordinates are used privately for matching.</p><div className="production-actions"><button className="primary" disabled={busy || loading}>{busy ? 'Working...' : 'Publish listing'}</button></div>
        </form>
        <h2>My listings</h2>{!loading && !error && (mine.length ? mine.map(r => record(r, true)) : <p className="mi-empty">No recorded listings.</p>)}
      </>}
      {tab === 'demand' && insights && !loading && !error && <>
        <p className="muted">{insights.notice}</p>
        {insights.rows.length ? <div className="mi-table-wrap"><table className="mi-table"><thead><tr><th>Crop / province</th><th>Month</th><th>Buyer requests</th><th>Requested kg</th><th>Recorded completed orders</th><th>Completed kg</th><th>Offers</th><th>Failed searches</th><th>Historical ZAR / kg</th></tr></thead><tbody>{insights.rows.map(r => <tr key={`${r.crop}-${r.province}-${r.yearMonth || r.month}`}><td>{r.crop}<small>{r.province}</small></td><td>{r.yearMonth || r.month}</td><td>{r.buyerRequests}</td><td>{r.requestedKg.toLocaleString()}</td><td>{r.completedOrders ?? 0}</td><td>{(r.completedQuantityKg ?? 0).toLocaleString()}</td><td>{r.offers}</td><td>{r.failedSearches}</td><td>{money(r.observedPricePerKg)}{r.priceSources.map(url => <a key={url} href={url} target="_blank" rel="noreferrer">Market source</a>)}</td></tr>)}</tbody></table></div> : <p className="mi-empty">No recorded produce demand or market observations yet.</p>}
        {insights.pipeline && <p className="muted">Last analytics run: {new Date(insights.pipeline.completedAt).toLocaleString()}</p>}
        {insights.forecastStatus === 'pending_analytics' && <p className="muted">No current forecast has been published. Forecasts are prepared by the scheduled analytics run.</p>}
        {insights.forecasts?.length > 0 && <><h2>Observed-volume outlook</h2>{insights.forecasts.map(f => <div className="mi-forecast" key={`${f.crop}-${f.province}-${f.market}`}><h3>{f.crop} | {f.market}</h3><p className="muted">{f.basis}</p><ForecastQuality forecast={f} />{f.forecast.length > 0 ? <ul>{f.forecast.map(point => <li key={point.month}>{point.month}: {point.quantityKg.toLocaleString()} kg
          {point.lowerKg != null && point.upperKg != null && <> (historical error range: {point.lowerKg.toLocaleString()}–{point.upperKg.toLocaleString()} kg)</>}</li>)}</ul> : <p>No forecast available.</p>}<p className="muted">{f.notice}</p></div>)}</>}
      </>}
    </section>
  </>;
}
