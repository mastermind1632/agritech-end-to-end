import { useEffect, useMemo, useRef, useState } from 'react';
import { privacyApi, statementApi } from '../lib/api';
import { BarChart, BarList } from '../components/Charts';
import { SkeletonCards, SkeletonLines } from '../components/Skeleton';
import EmptyState from '../components/EmptyState';
import Badge, { statusTone } from '../components/Badge';
import Tabs from '../components/Tabs';
import { useToast } from '../components/Toast';
import { date, money, monthLabel, titleCase } from '../lib/format';
import { printReport, table } from '../lib/print';
import useLang from '../hooks/useLang';
import { tr } from '../lib/i18n';

const SOURCE_LABEL = { RULE: 'Merchant rule', LEARNED: 'Your earlier choice', ML: 'Learned from your history', AI: 'AI suggestion', USER: 'You', MARKET_MATCH: 'Farm Market sale', DEFAULT: 'Unsure' };

/* ------------------------------------------------------------------ upload */

function Upload({ onDone }) {
  const toast = useToast();
  const { t } = useLang();
  const input = useRef(null);
  const [consented, setConsented] = useState(null);
  const [agree, setAgree] = useState(false);
  const [keep, setKeep] = useState(false);
  const [busy, setBusy] = useState(false);
  const [drag, setDrag] = useState(false);
  useEffect(() => {
    privacyApi.consents().then((list) => setConsented(list.find((c) => c.purpose === 'STATEMENT_PROCESSING'))).catch(() => setConsented({}));
  }, []);
  const granted = consented?.granted;

  const send = async (file) => {
    if (!file) return;
    if (!granted && !agree) { toast.error(t('Please tick the consent box first.')); return; }
    setBusy(true);
    try {
      const res = await statementApi.upload(file, agree || granted, keep);
      if (res.statement.status === 'FAILED') toast.error(t(res.statement.message), { title: t('We could not read that file') });
      else toast.success(t(res.statement.message), { title: t('Statement read') });
      onDone(res);
    } catch (e) { toast.error(e.message); }
    setBusy(false);
  };

  return (
    <div className="fm-panel">
      <div className={`dropzone ${drag ? 'drag' : ''} ${busy ? 'busy' : ''}`}
           onDragOver={(e) => { e.preventDefault(); setDrag(true); }} onDragLeave={() => setDrag(false)}
           onDrop={(e) => { e.preventDefault(); setDrag(false); send(e.dataTransfer.files?.[0]); }}
           onClick={() => !busy && input.current?.click()} role="button" tabIndex={0}
           onKeyDown={(e) => { if (e.key === 'Enter') input.current?.click(); }}>
        <input ref={input} type="file" hidden accept=".csv,.txt,.pdf,image/jpeg,image/png" onChange={(e) => { send(e.target.files?.[0]); e.target.value = ''; }} />
        {busy ? (
          <><div className="spinner" /><b>{t('Reading your statement…')}</b><span>{t('Extracting lines, checking the balance and sorting each transaction into a category.')}</span></>
        ) : (
          <><span className="dz-icon">⇪</span><b>{t('Drop a bank statement here, or tap to choose')}</b>
            <span>{t("Best: the CSV export from your bank's app. Also works: the bank's PDF statement, a scanned statement or a clear photo.")}</span></>
        )}
      </div>
      <div className="consent-box">
        {granted ? (
          <p className="muted">✓ {t('You agreed on {date} that we may read your statements. You can withdraw this in Settings.', { date: date(consented.at) })}</p>
        ) : (
          <label className="check"><input type="checkbox" checked={agree} onChange={(e) => setAgree(e.target.checked)} />
            <span>{t('I agree that AgriTech may read this statement to categorise and analyse my farm spending (POPIA). Only I can see the results.')}</span></label>
        )}
        <label className="check"><input type="checkbox" checked={keep} onChange={(e) => setKeep(e.target.checked)} />
          <span>{t('Keep the original file in my encrypted document vault. (If unticked, the file is discarded once it has been read.)')}</span></label>
      </div>
      <details className="help">
        <summary>{t('How do I get a CSV from my bank?')}</summary>
        <p>{t("Most banking apps and internet banking let you download transactions as CSV (sometimes called \"Excel\" or \"spreadsheet\") under the account's transaction history or statements. Choose the period you want, usually one month. PDF statements downloaded from the bank also work well because they contain real text.")}</p>
      </details>
    </div>
  );
}

/* ------------------------------------------------------------------ review */

function Review({ id, onBack, onChanged }) {
  const toast = useToast();
  const { t: T } = useLang();
  const [d, setD] = useState(null);
  const [filter, setFilter] = useState('all');
  const [similar, setSimilar] = useState(true);
  const [busy, setBusy] = useState(false);
  const load = () => statementApi.get(id).then(setD).catch((e) => toast.error(e.message));
  useEffect(() => { load(); /* eslint-disable-next-line */ }, [id]);
  const s = d?.statement;
  const editable = s?.status === 'REVIEW';

  const shown = useMemo(() => (d?.transactions || []).filter((t) => (
    filter === 'review' ? t.needsReview : filter === 'flagged' ? !!t.flag : filter === 'in' ? Number(t.amount) > 0 : filter === 'out' ? Number(t.amount) < 0 : true)), [d, filter]);

  const update = async (t, patch) => {
    try {
      await statementApi.updateTxn(t.id, { ...patch, applyToSimilar: patch.category ? similar : false });
      load();
    } catch (e) { toast.error(e.message); }
  };
  const confirm = async () => {
    const left = d.transactions.filter((t) => t.needsReview && !t.flag).length;
    if (left && !window.confirm(T('{n} transaction(s) still need review. Import anyway with the suggested categories?', { n: left }))) return;
    setBusy(true);
    try { setD(await statementApi.confirm(id)); toast.success(T('Imported into My Finances. Your analysis is updated.')); onChanged(); }
    catch (e) { toast.error(e.message); }
    setBusy(false);
  };
  const reopen = async () => {
    if (!window.confirm(T("Remove this statement's entries from My Finances so you can edit it again?"))) return;
    try { setD(await statementApi.reopen(id)); onChanged(); } catch (e) { toast.error(e.message); }
  };

  if (!d) return <SkeletonLines lines={10} />;
  const cats = d.categories;
  const expenseCats = Object.entries(cats).filter(([k]) => !['PRODUCE_SALES', 'GRANTS', 'OTHER_INCOME'].includes(k));
  const incomeCats = Object.entries(cats).filter(([k]) => ['PRODUCE_SALES', 'GRANTS', 'OTHER_INCOME', 'TRANSFER'].includes(k));
  const counts = { review: d.transactions.filter((t) => t.needsReview).length, flagged: d.transactions.filter((t) => t.flag).length };

  return (
    <>
      <button className="link-btn" onClick={onBack}>{T('← All statements')}</button>
      <div className="fm-panel statement-head" style={{ marginTop: 10 }}>
        <div>
          <h3>{s.bank || T('Bank statement')}{s.accountHint ? ` ····${s.accountHint}` : ''}</h3>
          <p className="muted">{s.fileName} · {s.periodStart ? T('{from} to {until}', { from: date(s.periodStart), until: date(s.periodEnd) }) : ''} · {T('read by {method}', { method: s.extractionMethod === 'csv' ? 'CSV' : s.extractionMethod === 'ocr' ? T('OCR (photo/scan)') : T('PDF text') })}</p>
          <div className="row-wrap" style={{ marginTop: 8 }}>
            <Badge tone={statusTone(s.status)}>{titleCase(s.status)}</Badge>
            <Badge tone={s.balanceCheck === 'PASSED' ? 'good' : s.balanceCheck === 'FAILED' ? 'bad' : 'neutral'}
                   title="Opening balance + all transactions should equal the closing balance">
              {T(s.balanceCheck === 'PASSED' ? 'Balance check: passed' : s.balanceCheck === 'FAILED' ? 'Balance check: does not add up' : 'Balance check: not possible')}</Badge>
          </div>
        </div>
        <div className="summary-grid compact">
          <div><span>{T('Money in')}</span><strong className="positive">{money(s.moneyIn, 0)}</strong></div>
          <div><span>{T('Money out')}</span><strong>{money(s.moneyOut, 0)}</strong></div>
          <div><span>{T('Opening')}</span><strong>{s.openingBalance == null ? '—' : money(s.openingBalance, 0)}</strong></div>
          <div><span>{T('Closing')}</span><strong>{s.closingBalance == null ? '—' : money(s.closingBalance, 0)}</strong></div>
        </div>
      </div>
      {s.message && <div className={`notice inline ${s.balanceCheck === 'FAILED' ? 'warn' : ''}`}>{T(s.message)}</div>}
      <div className="row-wrap" style={{ justifyContent: 'space-between', margin: '12px 0' }}>
        <div className="segmented">
          {[['all', T('All ({n})', { n: d.transactions.length })], ['review', T('Needs review ({n})', { n: counts.review })], ['flagged', T('Flagged ({n})', { n: counts.flagged })], ['in', T('Money in')], ['out', T('Money out')]].map(([k, l]) => (
            <button key={k} className={filter === k ? 'on' : ''} onClick={() => setFilter(k)}>{l}</button>
          ))}
        </div>
        {editable && <label className="check inline"><input type="checkbox" checked={similar} onChange={(e) => setSimilar(e.target.checked)} /><span>{T('Apply my changes to the same merchant')}</span></label>}
      </div>
      <div className="table-wrap panel" style={{ padding: 0 }}>
        <table className="txn-table">
          <thead><tr><th>{T('Date')}</th><th>{T('Description')}</th><th className="right">{T('Amount')}</th><th>{T('Category')}</th><th>{T('Crop')}</th></tr></thead>
          <tbody>
            {shown.map((t) => (
              <tr key={t.id} className={t.needsReview ? 'needs-review' : ''}>
                <td>{date(t.date)}</td>
                <td>
                  <b>{t.description}</b>
                  <div className="txn-meta">
                    <span className={`conf ${t.confidence >= 0.85 ? 'high' : t.confidence >= 0.7 ? 'mid' : 'low'}`} title={T('Confidence {pct}%', { pct: Math.round(t.confidence * 100) })}>{T(SOURCE_LABEL[t.source] || t.source)}</span>
                    {t.flag && <Badge tone="bad" title={t.flagReason}>{t.flag === 'DUPLICATE' ? 'Possible duplicate' : 'Unusual amount'}</Badge>}
                    {t.flag && editable && <button className="link-btn" onClick={() => update(t, { clearFlag: true })}>{t.flag === 'DUPLICATE' ? T('Not a duplicate') : T('Looks fine')}</button>}
                    {t.matchedOrderId && <span className="muted">{T('Order #{id} (already in your income)', { id: t.matchedOrderId })}</span>}
                    {t.ledgerType && <span className="muted">✓ {T('in My Finances')}</span>}
                  </div>
                  {t.flagReason && <small className="muted">{T(t.flagReason)}</small>}
                </td>
                <td className={`right ${Number(t.amount) > 0 ? 'positive' : ''}`}><b>{Number(t.amount) > 0 ? '+' : '−'}{money(Math.abs(t.amount))}</b></td>
                <td>
                  <select className="filter compact" disabled={!editable} value={t.category} onChange={(e) => update(t, { category: e.target.value })}>
                    {(Number(t.amount) < 0 ? expenseCats : incomeCats).map(([k, l]) => <option key={k} value={k}>{T(l)}</option>)}
                  </select>
                </td>
                <td>
                  {Number(t.amount) < 0 && !['PERSONAL', 'TRANSFER', 'BANK_FEES', 'LOAN'].includes(t.category) ? (
                    <select className="filter compact" disabled={!editable} value={t.crop || ''} onChange={(e) => update(t, { crop: e.target.value })}>
                      <option value="">—</option>
                      {d.crops.map((c) => <option key={c} value={c}>{T(c)}</option>)}
                      {t.crop && !d.crops.includes(t.crop) && <option>{t.crop}</option>}
                    </select>
                  ) : <span className="muted">—</span>}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {!shown.length && <EmptyState icon="✓" title="Nothing here" text="No transactions match this filter." />}
      </div>
      {!d.crops.length && <p className="muted" style={{ marginTop: 8 }}>{T('Tip: add crops to your business plan (Business Builder) to tag costs per crop.')}</p>}
      <div className="row-wrap sticky-actions">
        {editable && <button className="primary" disabled={busy} onClick={confirm}>{busy ? T('Importing…') : T('Confirm and import into My Finances')}</button>}
        {s.status === 'CONFIRMED' && <button className="secondary" onClick={reopen}>{T('Edit again (undo import)')}</button>}
      </div>
    </>
  );
}

/* ------------------------------------------------------------------ analysis */

function Analysis({ farmer, setPage }) {
  const toast = useToast();
  const { t } = useLang();
  const [a, setA] = useState(null);
  const [rule, setRule] = useState({ category: 'FERTILISER', method: 'BY_HECTARES', crop: '' });
  const [cats, setCats] = useState({});
  const load = () => statementApi.analysis().then(setA).catch((e) => toast.error(e.message));
  useEffect(() => { load(); statementApi.categories().then((c) => setCats(c.expense)).catch(() => {}); /* eslint-disable-next-line */ }, []);
  if (!a) return <SkeletonLines lines={10} />;
  const r = a.report;
  if (!a.transactions) {
    return <EmptyState icon="▥" title="No confirmed statements yet" text="Upload a statement and confirm it. Your monthly summary, cost per crop and unusual-expense flags appear here." />;
  }
  const saveRule = async (e) => {
    e.preventDefault();
    try { await statementApi.saveRule(rule); toast.success(t('Split rule saved.')); load(); } catch (err) { toast.error(err.message); }
  };
  const lender = () => printReport(t('Farm financial summary'), [
    { title: t('Summary'), html: table([t('Measure'), t('Amount')], [[t('Farm income'), money(r.totalIncome)], [t('Farm expenses'), money(r.totalExpenses)],
      [t('Net farm result'), money(Number(r.totalIncome) - Number(r.totalExpenses))], [t('Personal spending (excluded)'), money(r.personalSpend)]]) },
    { title: t('Monthly'), html: table(['Month', 'Income', 'Expenses', 'Net'].map(tr), r.months.map((m) => [monthLabel(m.month), money(m.income), money(m.expenses), money(m.net)])) },
    { title: t('Spending by category'), html: table(['Category', 'Amount', 'Share'].map(tr), r.categories.map((c) => [t(c.label), money(c.amount), `${Math.round(c.share * 100)}%`])) },
    { title: t('Cost per crop'), html: table(['Crop', 'Total cost', 'Hectares', 'Cost per hectare'].map(tr), r.crops.map((c) => [t(c.crop), money(c.total), c.hectares ?? '', c.perHectare == null ? '' : money(c.perHectare)])) },
    { title: t('Notes'), html: `<ul>${r.insights.map((i) => `<li>${t(i.text).replace(/</g, '&lt;')}</li>`).join('')}</ul>` }
  ], t('{name} · from {statements} bank statement(s), {transactions} transactions', { name: farmer.name, statements: a.statements, transactions: a.transactions }));

  return (
    <>
      <div className="row-wrap" style={{ justifyContent: 'flex-end', marginBottom: 12 }}>
        <button className="secondary" onClick={() => statementApi.exportCsv().catch((e) => toast.error(e.message))}>CSV</button>
        <button className="secondary" onClick={() => statementApi.exportXlsx().catch((e) => toast.error(e.message))}>{t('Excel (for your accountant)')}</button>
        <button className="primary" onClick={lender}>{t('Lender report (PDF)')}</button>
      </div>
      <section className="stats" style={{ padding: 0, marginBottom: 16 }}>
        <div className="stat-card"><span>{t('Farm income')}</span><strong className="positive">{money(r.totalIncome, 0)}</strong><small>{t('From statements')}</small></div>
        <div className="stat-card"><span>{t('Farm expenses')}</span><strong>{money(r.totalExpenses, 0)}</strong><small>{t('Transfers and personal excluded')}</small></div>
        <div className="stat-card"><span>{t('Net farm result')}</span><strong>{money(Number(r.totalIncome) - Number(r.totalExpenses), 0)}</strong><small>{t('Income minus farm costs')}</small></div>
        <div className="stat-card"><span>{t('Personal spending')}</span><strong>{money(r.personalSpend, 0)}</strong><small>{t('Paid from this account')}</small></div>
      </section>
      {r.insights.length > 0 && (
        <div className="insights">{r.insights.map((i) => <div key={i.text} className={`insight ${i.level}`}>{i.level === 'warning' ? '!' : i.level === 'good' ? '✓' : 'i'} {t(i.text)}</div>)}</div>
      )}
      <div className="dashboard-grid flush">
        <section className="panel">
          <div className="panel-head"><div><p className="eyebrow">{t('MONTH BY MONTH')}</p><h2>{t('Cash flow')}</h2></div></div>
          <BarChart rows={r.months} series={[{ key: 'income', label: 'Income', color: '#3d9d6c' }, { key: 'expenses', label: 'Farm expenses', color: '#c98a3a' }]} />
        </section>
        <section className="panel">
          <div className="panel-head"><div><p className="eyebrow">{t('WHERE THE MONEY GOES')}</p><h2>{t('By category')}</h2></div></div>
          <BarList items={r.categories.slice(0, 8)} note={(c) => `${Math.round(c.share * 100)}% · ${t('{n} transactions', { n: c.count })}`} />
        </section>
      </div>
      <section className="panel" style={{ marginTop: 16 }}>
        <div className="panel-head"><div><p className="eyebrow">{t('COST PER CROP')}</p><h2>{t('What each crop costs to grow')}</h2></div>
          <button className="text-button" onClick={() => setPage('companion')}>{t('Use in business plan →')}</button></div>
        {!r.crops.length ? <p className="muted">{t('Tag transactions with a crop in the review screen, or add a split rule below, to see cost per crop and per hectare.')}</p> : (
          <div className="table-wrap"><table>
            <thead><tr><th>{t('Crop')}</th><th className="right">{t('Direct')}</th><th className="right">{t('Shared (split)')}</th><th className="right">{t('Total')}</th><th className="right">{t('Hectares')}</th><th className="right">{t('Per hectare')}</th></tr></thead>
            <tbody>{r.crops.map((c) => (
              <tr key={c.crop}><td><b>{t(c.crop)}</b></td><td className="right">{money(c.direct, 0)}</td><td className="right">{money(c.allocated, 0)}</td>
                <td className="right"><b>{money(c.total, 0)}</b></td><td className="right">{c.hectares ?? '—'}</td><td className="right">{c.perHectare == null ? '—' : money(c.perHectare, 0)}</td></tr>
            ))}</tbody>
          </table></div>
        )}
        {Number(r.unallocated) > 0 && <p className="muted">{t('Not linked to a crop:')} {money(r.unallocated, 0)}</p>}
        <form className="row-wrap rule-form" onSubmit={saveRule}>
          <span>{t('Split')}</span>
          <select className="filter compact" value={rule.category} onChange={(e) => setRule({ ...rule, category: e.target.value })}>
            {Object.entries(cats).filter(([k]) => !['PERSONAL', 'TRANSFER'].includes(k)).map(([k, l]) => <option key={k} value={k}>{t(l)}</option>)}</select>
          <select className="filter compact" value={rule.method} onChange={(e) => setRule({ ...rule, method: e.target.value })}>
            <option value="BY_HECTARES">{t('by hectares planted')}</option><option value="FIXED_CROP">{t('all to one crop')}</option></select>
          {rule.method === 'FIXED_CROP' && <input className="filter compact" placeholder={t('Crop')} value={rule.crop} onChange={(e) => setRule({ ...rule, crop: e.target.value })} />}
          <button className="secondary small">{t('Save rule')}</button>
        </form>
        {a.rules.length > 0 && <p className="muted">{t('Rules:')} {a.rules.map((x) => `${t(x.categoryLabel)} → ${x.method === 'BY_HECTARES' ? t('by hectares') : t(x.crop)}`).join(' · ')}
          {a.rules.map((x) => <button key={x.id} className="link-btn" style={{ marginLeft: 10 }} onClick={() => statementApi.deleteRule(x.id).then(load)}>{t('remove {name}', { name: t(x.categoryLabel) })}</button>)}</p>}
      </section>
      {r.flags.length > 0 && (
        <section className="panel" style={{ marginTop: 16 }}>
          <div className="panel-head"><div><p className="eyebrow">{t('CHECK THESE')}</p><h2>{t('Unusual expenses and possible duplicates')}</h2></div></div>
          {r.flags.map((f) => <div key={f.id + f.type} className="list-row"><span className="round-icon">!</span><div><b>{f.type === 'DUPLICATE' ? t('Possible duplicate') : t('Unusual amount')}</b><p>{t(f.reason)}</p></div></div>)}
        </section>
      )}
    </>
  );
}

/* ------------------------------------------------------------------ page */

export default function Statements({ farmer, setPage }) {
  const toast = useToast();
  const { t } = useLang();
  const [tab, setTab] = useState('statements');
  const [list, setList] = useState(null);
  const [open, setOpen] = useState(null);
  const load = () => statementApi.list().then(setList).catch((e) => { toast.error(e.message); setList([]); });
  useEffect(() => { load(); /* eslint-disable-next-line */ }, []);
  const remove = async (s) => {
    const keep = s.status === 'CONFIRMED' && window.confirm(t('Keep the entries it added to My Finances?\nOK = keep them, Cancel = remove them too.'));
    if (!window.confirm(t('Delete "{item}"?', { item: s.fileName }))) return;
    try { await statementApi.remove(s.id, keep); toast.success(t('Statement deleted.')); load(); } catch (e) { toast.error(e.message); }
  };
  const review = (list || []).filter((s) => s.status === 'REVIEW').length;

  return (
    <>
      <div className="page-head">
        <div>
          <p className="eyebrow">{t('EXPENSE ANALYSIS · OCR + AI')}</p>
          <h1>{t('Know where every rand goes.')}</h1>
          <p className="muted">{t('Upload a bank statement. We read it, check that the balances add up, sort every transaction into a farm category and show your cost per crop, trends and anything unusual.')}</p>
        </div>
        {!open && <Tabs value={tab} onChange={setTab} tabs={[['statements', 'Statements', review], ['analysis', 'Analysis']]} />}
      </div>
      <div className="fm-body">
        {open ? <Review id={open} onBack={() => { setOpen(null); load(); }} onChanged={load} />
          : tab === 'analysis' ? <Analysis farmer={farmer} setPage={setPage} /> : (
            <>
              <Upload onDone={(res) => { load(); if (res.statement.status !== 'FAILED') setOpen(res.statement.id); }} />
              {!list ? <SkeletonCards count={2} /> : !list.length ? (
                <EmptyState icon="≣" title="No statements yet" text="Your first statement takes about a minute. Try last month's CSV export from your bank's app." />
              ) : (
                <div className="card-grid" style={{ marginTop: 16 }}>
                  {list.map((s) => (
                    <article key={s.id} className="panel statement-card">
                      <div className="row-wrap" style={{ justifyContent: 'space-between' }}>
                        <Badge tone={statusTone(s.status)}>{titleCase(s.status)}</Badge>
                        <Badge tone={s.balanceCheck === 'PASSED' ? 'good' : s.balanceCheck === 'FAILED' ? 'bad' : 'neutral'}>{t(s.balanceCheck === 'PASSED' ? 'Balance passed' : s.balanceCheck === 'FAILED' ? 'Balance failed' : 'Balance not checked')}</Badge>
                      </div>
                      <h3>{s.bank || t('Statement')}{s.accountHint ? ` ····${s.accountHint}` : ''}</h3>
                      <p className="muted">{s.periodStart ? `${date(s.periodStart)} – ${date(s.periodEnd)}` : s.fileName}</p>
                      {s.status === 'FAILED' ? <p className="warn-text">{t(s.message)}</p> : (
                        <p>{t('{n} transactions · in {moneyIn} · out {moneyOut}', { n: s.transactionCount, moneyIn: money(s.moneyIn, 0), moneyOut: money(s.moneyOut, 0) })}
                          {s.needsReview > 0 && s.status === 'REVIEW' && <><br /><b>{t('{n} need your review', { n: s.needsReview })}</b></>}</p>
                      )}
                      <div className="row-wrap">
                        {s.status !== 'FAILED' && <button className="primary small" onClick={() => setOpen(s.id)}>{s.status === 'REVIEW' ? t('Review') : t('View')}</button>}
                        <button className="link-btn" onClick={() => remove(s)}>{t('Delete')}</button>
                      </div>
                    </article>
                  ))}
                </div>
              )}
            </>
          )}
      </div>
    </>
  );
}
