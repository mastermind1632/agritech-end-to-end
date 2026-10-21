import { useEffect, useState } from 'react';
import StatCard from '../components/StatCard';
import { BarChart, Progress, ProgressRing } from '../components/Charts';
import { SkeletonLines } from '../components/Skeleton';
import EmptyState from '../components/EmptyState';
import Badge from '../components/Badge';
import { farmMarketApi, financeApi, groupApi, insightsApi, adminApi } from '../lib/api';
import { compactMoney, date, money } from '../lib/format';
import { describe, farmingHint, forecastFor } from '../lib/weather';
import useLang from '../hooks/useLang';
import { weekdayShort } from '../lib/i18n';

function NextSteps({ steps, setPage, loading }) {
  const { t } = useLang();
  return (
    <section className="panel">
      <div className="panel-head"><div><p className="eyebrow">{t('JUST FOR YOU')}</p><h2>{t('nextSteps')}</h2></div></div>
      {loading ? <SkeletonLines lines={4} /> : !steps?.length ? (
        <EmptyState icon="✓" title={t('allCaughtUp')} text="New tasks from orders, deadlines and your statements will appear here." />
      ) : steps.slice(0, 6).map((s) => (
        <button key={s.id} className={`step-row ${s.priority}`} onClick={() => setPage(s.page)}>
          <span className="step-dot" />
          <div><b>{t(s.title)}</b><p>{t(s.text)}</p></div>
          <span className="step-go">→</span>
        </button>
      ))}
    </section>
  );
}

function Weather({ place }) {
  const { t } = useLang();
  const [w, setW] = useState(null);
  const [error, setError] = useState('');
  useEffect(() => {
    let live = true;
    forecastFor(place).then((d) => live && setW(d)).catch(() => live && setError(t('The forecast is not available right now. Check your signal and try again later.')));
    return () => { live = false; };
  }, [place]);
  return (
    <section className="panel weather">
      <div className="panel-head"><div><p className="eyebrow">{t('weather').toUpperCase()}</p><h2>{w?.place || place || t('Your farm')}</h2></div>
        {w && <span className="weather-now">{describe(w.current?.weather_code)[1]} {Math.round(w.current?.temperature_2m)}°C</span>}</div>
      {error ? <p className="muted">{error}</p> : !w ? <SkeletonLines lines={3} /> : (
        <>
          <div className="weather-days">
            {w.daily.time.map((d, i) => (
              <div key={d}>
                <span>{i === 0 ? t('Today') : weekdayShort(d)}</span>
                <b title={describe(w.daily.weather_code[i])[0]}>{describe(w.daily.weather_code[i])[1]}</b>
                <span>{Math.round(w.daily.temperature_2m_max[i])}° / {Math.round(w.daily.temperature_2m_min[i])}°</span>
                <small>{w.daily.precipitation_probability_max?.[i] ?? 0}% · {Math.round(w.daily.precipitation_sum[i] || 0)} mm</small>
              </div>
            ))}
          </div>
          <p className="weather-hint">{farmingHint(w.daily)}</p>
          <p className="source-note">{t('Forecast: Open-Meteo')}</p>
        </>
      )}
    </section>
  );
}

function PriceBoard({ setPage }) {
  const { t } = useLang();
  const [rows, setRows] = useState(null);
  useEffect(() => { farmMarketApi.prices(8).then(setRows).catch(() => setRows([])); }, []);
  return (
    <section className="panel">
      <div className="panel-head"><div><p className="eyebrow">{t('prices').toUpperCase()}</p><h2>{t('Prices on Farm Market')}</h2></div>
        <button className="text-button" onClick={() => setPage('farmmarket')}>{t('Browse →')}</button></div>
      {rows === null ? <SkeletonLines lines={4} /> : !rows.length ? (
        <EmptyState icon="▤" title="No prices yet" text="Prices appear as farmers list produce." action="List produce" onAction={() => setPage('farmmarket')} />
      ) : (
        <div className="table-wrap"><table>
          <thead><tr><th>{t('Product')}</th><th className="right">{t('Asking')}</th><th className="right">{t('Range')}</th><th className="right">{t('Sold (90 days)')}</th></tr></thead>
          <tbody>{rows.map((r) => (
            <tr key={r.product + r.unit}>
              <td><b>{t(r.product)}</b> <span className="muted">/ {t(r.unit)}</span></td>
              <td className="right">{money(r.averageAsking)}</td>
              <td className="right muted">{money(r.lowest)} – {money(r.highest)}</td>
              <td className="right">{r.averageSold ? `${money(r.averageSold)} (${r.sales})` : '—'}</td>
            </tr>))}
          </tbody>
        </table></div>
      )}
    </section>
  );
}

function ModuleTiles({ o, setPage }) {
  const { t } = useLang();
  const c = o?.companion;
  const ex = o?.exports || {};
  const st = o?.statements || {};
  const mk = o?.marketplace || {};
  return (
    <div className="module-grid">
      <button className="module-tile" onClick={() => setPage('companion')}>
        <ProgressRing value={c?.percent || 0} size={64} />
        <div><b>{t('Business Builder')}</b>
          <span>{!c?.intakeCompleted ? t('Answer 10 questions to start') : t('{done} of {total} steps done', { done: c.done, total: c.applicable })}</span>
          {c?.verified && <Badge tone="good">Verified business</Badge>}
          {c?.nextDeadline && <small>{t('Next:')} {t(c.nextDeadline.title)} · {date(c.nextDeadline.dueDate)}</small>}
        </div>
      </button>
      <button className="module-tile" onClick={() => setPage('statements')}>
        <span className="tile-icon">≣</span>
        <div><b>{t('Expense Analysis')}</b>
          <span>{st.statements ? t(st.statements > 1 ? '{n} statements analysed' : '{n} statement analysed', { n: st.statements }) : t('Upload a bank statement')}</span>
          {st.needsReview > 0 && <Badge tone="warn">{t('{n} to review', { n: st.needsReview })}</Badge>}
          {st.latestPeriodEnd && <small>{t('Up to {date}', { date: date(st.latestPeriodEnd) })}</small>}
        </div>
      </button>
      <button className="module-tile" onClick={() => setPage('export')}>
        <ProgressRing value={ex.bestReadiness || 0} size={64} color="var(--gold)" />
        <div><b>{t('Export Readiness')}</b>
          <span>{ex.activePlans ? t(ex.activePlans > 1 ? '{n} active plans' : '{n} active plan', { n: ex.activePlans }) : t('Check a product and market')}</span>
          {ex.exportReady && <Badge tone="good">Export-ready</Badge>}
        </div>
      </button>
      <button className="module-tile" onClick={() => setPage('farmmarket')}>
        <span className="tile-icon">⛟</span>
        <div><b>{t('Farm Market')}</b>
          <span>{t(mk.activeListings === 1 ? '{n} active listing' : '{n} active listings', { n: mk.activeListings || 0 })} · {t('{n} sales', { n: mk.completedSales || 0 })}</span>
          {mk.ordersToAnswer > 0 && <Badge tone="bad">{t(mk.ordersToAnswer > 1 ? '{n} orders waiting' : '{n} order waiting', { n: mk.ordersToAnswer })}</Badge>}
        </div>
      </button>
    </div>
  );
}

function AdminStrip({ setPage }) {
  const { t } = useLang();
  const [s, setS] = useState(null);
  useEffect(() => { adminApi.stats().then(setS).catch(() => {}); }, []);
  if (!s) return null;
  return (
    <section className="panel admin-strip">
      <div className="panel-head"><div><p className="eyebrow">{t('PLATFORM')}</p><h2>{t('Today on the platform')}</h2></div>
        <button className="text-button" onClick={() => setPage('admin')}>{t('Open admin console →')}</button></div>
      <div className="summary-grid">
        <div><span>{t('Users')}</span><strong>{s.totalUsers}</strong></div>
        <div><span>{t('Active listings')}</span><strong>{s.activeListings}</strong></div>
        <div><span>{t('Completed sales')}</span><strong>{compactMoney(s.completedSalesValue)}</strong></div>
        <div><span>{t('Open disputes')}</span><strong>{s.openDisputes}</strong></div>
        <div><span>{t('Verifications waiting')}</span><strong>{s.pendingVerifications}</strong></div>
        <div><span>{t('Content to review')}</span><strong>{s.pendingContentChanges}</strong></div>
      </div>
    </section>
  );
}

export default function Dashboard({ farmer, setPage }) {
  const { t } = useLang();
  const role = farmer.role || 'FARMER';
  const isFarmer = role === 'FARMER' || role === 'ADMIN';
  const [overview, setOverview] = useState(null);
  const [months, setMonths] = useState([]);
  const [summary, setSummary] = useState(null);
  const [groups, setGroups] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    let live = true;
    (async () => {
      setLoading(true); setError('');
      try {
        const [o, g] = await Promise.all([insightsApi.overview(), groupApi.list('open').catch(() => [])]);
        if (!live) return;
        setOverview(o); setGroups(g || []);
        if (isFarmer) {
          const [m, s] = await Promise.all([financeApi.monthly(6), financeApi.summary(farmer.id)]);
          if (live) { setMonths(m || []); setSummary(s); }
        }
      } catch (e) {
        if (live) setError(e.message || t('Unable to load your overview.'));
      } finally {
        if (live) setLoading(false);
      }
    })();
    return () => { live = false; };
  }, [farmer.id, isFarmer]);

  const income = Number(summary?.totalIncome || 0);
  const expenses = Number(summary?.totalExpenses || 0);
  const last3 = months.slice(-3).reduce((a, m) => a + Number(m.net || 0), 0);
  const seg = overview?.segment;

  return (
    <>
      <div className="page-head">
        <div>
          <p className="eyebrow">{t('YOUR FARM TODAY')}</p>
          <h1>{t('goodDay')}, {farmer.name?.split(' ')[0] || t('Farmer')}.</h1>
          <p className="muted">{seg ? <>{t('You are in the')} <b>{t(seg.name)}</b> {t('group.')} {t(seg.description)}</> : t('Here is how your farm is doing.')}</p>
        </div>
        <div className="head-actions">
          {isFarmer && <button className="secondary" onClick={() => setPage('statements')}>{t('Upload statement')}</button>}
          {isFarmer ? <button className="primary" onClick={() => setPage('finance')}>{t('+ Record transaction')}</button>
            : <button className="primary" onClick={() => setPage('farmmarket')}>{t('Browse produce')}</button>}
        </div>
      </div>

      {error && <div className="error page-error">{error}</div>}

      {isFarmer && (
        <section className="stats">
          <StatCard label="Total income" value={loading ? '…' : money(income, 0)} sub="All recorded sales" icon="arrow" positive />
          <StatCard label="Total expenses" value={loading ? '…' : money(expenses, 0)} sub="All recorded costs" icon="wallet" />
          <StatCard label="Net, last 3 months" value={loading ? '…' : money(last3, 0)} sub="Income minus expenses" icon="leaf" positive={last3 >= 0} />
          <StatCard label="Open group orders" value={loading ? '…' : groups.length} sub="Buy inputs together" icon="users" />
        </section>
      )}

      {role === 'ADMIN' && <div className="dash-section"><AdminStrip setPage={setPage} /></div>}

      {isFarmer && <div className="dash-section"><ModuleTiles o={overview} setPage={setPage} /></div>}

      <div className="dashboard-grid">
        <div className="stack">
          {isFarmer && (
            <section className="panel">
              <div className="panel-head"><div><p className="eyebrow">{t('CASH FLOW')}</p><h2>{t('Income and expenses, last 6 months')}</h2></div>
                <button className="text-button" onClick={() => setPage('finance')}>{t('Details →')}</button></div>
              {loading ? <SkeletonLines lines={5} /> : months.some((m) => Number(m.income) || Number(m.expenses)) ? (
                <BarChart rows={months} series={[{ key: 'income', label: 'Income', color: '#3d9d6c' }, { key: 'expenses', label: 'Expenses', color: '#c98a3a' }]} />
              ) : <EmptyState icon="▥" title="No transactions yet" text="Record a sale or expense, or upload a bank statement, to see your cash flow." action="Upload statement" onAction={() => setPage('statements')} />}
            </section>
          )}
          <PriceBoard setPage={setPage} />
          {seg?.recommendedModules?.length > 0 && (
            <section className="panel">
              <div className="panel-head"><div><p className="eyebrow">{t('RECOMMENDED FOR YOUR FARM')}</p><h2>{t('Services for "{name}"', { name: t(seg.name) })}</h2></div></div>
              <div className="quick-grid">
                {seg.recommendedModules.map((m) => (
                  <button key={m} onClick={() => setPage(m)}><b>{t(m === 'farmmarket' ? 'farmmarket' : m)}</b><span>{t('Open →')}</span></button>
                ))}
              </div>
              <p className="source-note">{t('Segments are rule-based until enough marketplace data exists to train the ML model (see Market Insights).')}</p>
            </section>
          )}
        </div>
        <div className="stack">
          <NextSteps steps={overview?.nextSteps} setPage={setPage} loading={loading} />
          <Weather place={farmer.location} />
          {(role === 'FARMER' || role === 'SUPPLIER' || role === 'ADMIN') && (
            <section className="panel">
              <div className="panel-head"><div><p className="eyebrow">{t('BUY TOGETHER')}</p><h2>{t('Open group orders')}</h2></div>
                <button className="text-button" onClick={() => setPage('groups')}>{t('View →')}</button></div>
              {loading ? <SkeletonLines lines={3} /> : !groups.length
                ? <EmptyState icon="♧" title="No open group orders" text="Start one to unlock a bulk discount with other farmers." action="Start a group order" onAction={() => setPage('groups')} />
                : groups.slice(0, 4).map((g) => (
                  <div className="list-row" key={g.id}>
                    <span className="round-icon">♧</span>
                    <div style={{ flex: 1 }}>
                      <b>{t('{pct}% off · {have} of {need} units', { pct: g.discountRate, have: g.currentQuantity, need: g.targetQuantity })}</b>
                      <Progress value={Math.round((g.currentQuantity / g.targetQuantity) * 100)} />
                      {g.deadline && <p>{t('Closes {date}', { date: date(g.deadline) })}</p>}
                    </div>
                  </div>
                ))}
            </section>
          )}
        </div>
      </div>
    </>
  );
}
