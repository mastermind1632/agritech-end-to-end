import { useEffect, useMemo, useState } from 'react';
import { companionApi, vaultApi } from '../lib/api';
import { BarChart, ProgressRing } from '../components/Charts';
import { SkeletonCards, SkeletonLines } from '../components/Skeleton';
import EmptyState from '../components/EmptyState';
import Badge, { statusTone } from '../components/Badge';
import Tabs from '../components/Tabs';
import { useToast } from '../components/Toast';
import { date, money, MONTHS, titleCase } from '../lib/format';
import { printReport, table } from '../lib/print';
import useLang from '../hooks/useLang';
import { monthName, tr } from '../lib/i18n';

const monthOptions = () => MONTHS.map((m, i) => <option key={m} value={i + 1}>{monthName(i)}</option>);

const STRUCTURES = [
  ['NONE', 'Not registered yet', 'I farm informally, in my own name.'],
  ['SOLE_PROPRIETOR', 'Sole proprietor', 'Trading in my own name, registered with SARS.'],
  ['PTY_LTD', 'Private company (Pty) Ltd', 'Registered with CIPC; limits my personal liability.'],
  ['COOPERATIVE', 'Co-operative', 'Owned by its members (five or more).']
];
const BANDS = [['UNDER_50K', 'Under R50 000 a year'], ['50K_TO_1M', 'R50 000 to R1 million'], ['1M_TO_5M', 'R1 million to R5 million'], ['OVER_5M', 'Over R5 million']];
const WATER = [['RAINFED', 'Rain only'], ['MUNICIPAL', 'Municipal water'], ['BOREHOLE', 'Borehole'], ['RIVER', 'River or stream'], ['DAM', 'Own dam'], ['IRRIGATION_SCHEME', 'Irrigation scheme']];
const STATUS_LABEL = { TODO: 'To do', IN_PROGRESS: 'Busy with it', DONE: 'Done', NOT_APPLICABLE: 'Does not apply' };

/* ------------------------------------------------------------------ intake questionnaire */

function Intake({ initial, onSaved }) {
  const toast = useToast();
  const { t } = useLang();
  const [step, setStep] = useState(0);
  const [f, setF] = useState(() => ({
    legalStructure: 'NONE', businessName: '', registrationNumber: '', registeredOn: '', taxNumber: '', vatRegistered: false,
    vatNumber: '', employees: 0, turnoverBand: 'UNDER_50K', crops: '', hectares: '', livestock: false, waterSource: 'RAINFED',
    irrigates: false, usesAgrochemicals: false, sellsToRetailers: false, exportInterest: false, hasBusinessAccount: false,
    financialYearEndMonth: 2, province: '', ...Object.fromEntries(Object.entries(initial || {}).filter(([, v]) => v !== null && v !== undefined))
  }));
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const set = (k) => (e) => setF({ ...f, [k]: e.target.type === 'checkbox' ? e.target.checked : e.target.value });
  const yes = (k, label) => (
    <label className="check"><input type="checkbox" checked={!!f[k]} onChange={set(k)} /><span>{t(label)}</span></label>
  );
  const registered = f.legalStructure === 'PTY_LTD' || f.legalStructure === 'COOPERATIVE';

  const save = async () => {
    setBusy(true); setError('');
    try {
      const body = {
        ...f, employees: Number(f.employees || 0), hectares: f.hectares === '' ? null : Number(f.hectares),
        financialYearEndMonth: Number(f.financialYearEndMonth), registeredOn: f.registeredOn || null,
        registrationNumber: f.registrationNumber || null, taxNumber: f.taxNumber || null
      };
      delete body.options; delete body.verified; delete body.verifiedAt; delete body.intakeCompleted;
      await companionApi.saveProfile(body);
      toast.success(t('Your personal checklist and compliance calendar are ready.'));
      onSaved();
    } catch (e) { setError(e.message); }
    setBusy(false);
  };

  const steps = ['Your business', 'Money and people', 'Your farm', 'Where you sell'];
  return (
    <div className="fm-panel wizard">
      <div className="wizard-steps">{steps.map((s, i) => <span key={s} className={i === step ? 'on' : i < step ? 'done' : ''}>{i + 1}. {t(s)}</span>)}</div>
      {error && <div className="error">{error}</div>}
      {step === 0 && (
        <>
          <h3>{t('How is your farm set up today?')}</h3>
          <div className="choice-grid">
            {STRUCTURES.map(([v, label, hint]) => (
              <label key={v} className={`choice ${f.legalStructure === v ? 'on' : ''}`}>
                <input type="radio" name="structure" checked={f.legalStructure === v} onChange={() => setF({ ...f, legalStructure: v })} />
                <b>{t(label)}</b><span>{t(hint)}</span>
              </label>
            ))}
          </div>
          <div className="form-grid">
            <label>{t('Business or farm name')}<input className="filter" value={f.businessName || ''} onChange={set('businessName')} placeholder={t('e.g. Maluleke Fresh Produce')} /></label>
            {registered && <label>{t('Registration number')}<input className="filter" value={f.registrationNumber || ''} onChange={set('registrationNumber')} placeholder="2021/123456/07" /></label>}
            {registered && <label>{t('Date registered')}<input className="filter" type="date" value={f.registeredOn || ''} onChange={set('registeredOn')} /></label>}
            <label>{t('Income tax number (if you have one)')}<input className="filter" value={f.taxNumber || ''} onChange={set('taxNumber')} inputMode="numeric" /></label>
          </div>
        </>
      )}
      {step === 1 && (
        <>
          <h3>{t('Money and people')}</h3>
          <div className="form-grid">
            <label>{t('Farm turnover (sales) per year')}
              <select className="filter" value={f.turnoverBand} onChange={set('turnoverBand')}>{BANDS.map(([v, l]) => <option key={v} value={v}>{t(l)}</option>)}</select></label>
            <label>{t('People employed (not counting yourself)')}<input className="filter" type="number" min="0" value={f.employees} onChange={set('employees')} /></label>
            <label>{t('Financial year ends in')}
              <select className="filter" value={f.financialYearEndMonth} onChange={set('financialYearEndMonth')}>{monthOptions()}</select></label>
            {f.vatRegistered && <label>{t('VAT number')}<input className="filter" value={f.vatNumber || ''} onChange={set('vatNumber')} /></label>}
          </div>
          {yes('vatRegistered', 'The farm is registered for VAT')}
          {yes('hasBusinessAccount', 'The farm has its own business bank account')}
        </>
      )}
      {step === 2 && (
        <>
          <h3>{t('Your farm')}</h3>
          <div className="form-grid">
            <label>{t('Main crops')}<input className="filter" value={f.crops || ''} onChange={set('crops')} placeholder={t('e.g. Tomatoes, Butternut')} /></label>
            <label>{t('Hectares farmed')}<input className="filter" type="number" min="0" step="0.1" value={f.hectares ?? ''} onChange={set('hectares')} /></label>
            <label>{t('Province')}<select className="filter" value={f.province || ''} onChange={set('province')}><option value="">{t('Choose')}</option>
              {(initial?.options?.province || []).map((p) => <option key={p}>{p}</option>)}</select></label>
            <label>{t('Main water source')}<select className="filter" value={f.waterSource || 'RAINFED'} onChange={set('waterSource')}>{WATER.map(([v, l]) => <option key={v} value={v}>{t(l)}</option>)}</select></label>
          </div>
          {yes('irrigates', 'I irrigate my crops')}
          {yes('usesAgrochemicals', 'I use pesticides, herbicides or fungicides')}
          {yes('livestock', 'I keep livestock')}
        </>
      )}
      {step === 3 && (
        <>
          <h3>{t('Where you sell')}</h3>
          {yes('sellsToRetailers', 'I sell (or want to sell) to supermarkets or other retailers')}
          {yes('exportInterest', 'I am interested in exporting')}
          <p className="muted" style={{ marginTop: 12 }}>{t('We use your answers only to build your checklist and reminders. You can change them any time.')}</p>
        </>
      )}
      <div className="row-wrap" style={{ marginTop: 16 }}>
        {step > 0 && <button className="secondary" onClick={() => setStep(step - 1)}>{t('Back')}</button>}
        {step < steps.length - 1
          ? <button className="primary" onClick={() => setStep(step + 1)}>{t('Next')}</button>
          : <button className="primary" onClick={save} disabled={busy}>{busy ? t('Building your checklist…') : t('Build my checklist')}</button>}
      </div>
    </div>
  );
}

/* ------------------------------------------------------------------ checklist */

function StepCard({ s, docs, onUpdate, setPage }) {
  const { t } = useLang();
  const [open, setOpen] = useState(s.status !== 'DONE');
  const [note, setNote] = useState(s.note || '');
  return (
    <article className={`step-card ${s.status.toLowerCase()}`}>
      <button className="step-card-head" onClick={() => setOpen(!open)} aria-expanded={open}>
        <span className={`step-check ${s.status === 'DONE' ? 'on' : ''}`}>{s.status === 'DONE' ? '✓' : ''}</span>
        <div><b>{t(s.title)}</b><span>{t(s.authority)}</span></div>
        <Badge tone={statusTone(s.status)}>{STATUS_LABEL[s.status]}</Badge>
      </button>
      {open && (
        <div className="step-card-body">
          <p>{t(s.description)}</p>
          {s.why && <p className="why"><b>{t('Why it matters:')}</b> {t(s.why)}</p>}
          <div className="row-wrap">
            {s.linkUrl && <a className="secondary btn-link" href={s.linkUrl} target="_blank" rel="noreferrer">{t(s.linkLabel || 'Official site')} ↗</a>}
            {s.moduleLink && <button className="secondary" onClick={() => setPage(s.moduleLink)}>{t('Open in AgriTech →')}</button>}
          </div>
          <div className="row-wrap" style={{ marginTop: 10 }}>
            {Object.entries(STATUS_LABEL).map(([v, l]) => (
              <button key={v} className={s.status === v ? 'primary small' : 'secondary small'} onClick={() => onUpdate(s.code, { status: v, note })}>{t(l)}</button>
            ))}
          </div>
          <div className="form-grid" style={{ marginTop: 10 }}>
            <label>{t('Note')}<input className="filter" value={note} maxLength={500} onChange={(e) => setNote(e.target.value)} onBlur={() => note !== (s.note || '') && onUpdate(s.code, { status: s.status, note })} placeholder={t('e.g. Application submitted 12 May')} /></label>
            <label>{t('Proof (from your documents)')}
              <select className="filter" value={s.documentId || ''} onChange={(e) => onUpdate(s.code, { status: s.status, note, documentId: e.target.value })}>
                <option value="">{t('None')}</option>
                {docs.map((d) => <option key={d.id} value={d.id}>{d.title}</option>)}
              </select>
            </label>
          </div>
          <p className="source-note">{s.reviewStatus === 'VERIFIED' ? t('Content checked {date}', { date: date(s.lastVerified) }) : t('Draft guidance - awaiting expert review. Confirm with the official source.')}</p>
        </div>
      )}
    </article>
  );
}

function Checklist({ data, docs, onUpdate, setPage }) {
  const { t } = useLang();
  const [stage, setStage] = useState(null);
  if (!data) return <SkeletonCards count={3} />;
  const stages = data.stages || [];
  const active = stage ?? (stages.find((s) => s.percent < 100)?.stage || stages[0]?.stage);
  const current = stages.find((s) => s.stage === active);
  return (
    <>
      <div className="fm-panel progress-hero">
        <ProgressRing value={data.percent} size={96} label="complete" />
        <div>
          <h3>{data.percent === 100 ? t('Your farm business is fully set up') : t('{done} of {total} steps done', { done: data.done, total: data.applicable })}</h3>
          <p className="muted">{t('Only the steps that apply to your answers are shown. Mark a step "does not apply" if your situation is different.')}</p>
        </div>
      </div>
      <div className="stage-strip">
        {stages.map((s) => (
          <button key={s.stage} className={`stage-pill ${s.stage === active ? 'on' : ''} ${s.percent === 100 ? 'done' : ''}`} onClick={() => setStage(s.stage)}>
            <span>{s.stage}</span>{t(s.name)}<em>{s.done}/{s.applicable}</em>
          </button>
        ))}
      </div>
      {current && current.steps.map((s) => <StepCard key={s.code} s={s} docs={docs} onUpdate={onUpdate} setPage={setPage} />)}
      <p className="disclaimer">{t(data.disclaimer)} <a href="https://www.saica.org.za" target="_blank" rel="noreferrer">{t('Find a registered accountant (SAICA)')}</a></p>
    </>
  );
}

/* ------------------------------------------------------------------ compliance calendar */

function Calendar({ items, reload }) {
  const toast = useToast();
  const { t } = useLang();
  const [form, setForm] = useState({ title: '', dueDate: '', authority: '' });
  if (!items) return <SkeletonLines lines={6} />;
  const add = async (e) => {
    e.preventDefault();
    try { await companionApi.addDeadline(form); setForm({ title: '', dueDate: '', authority: '' }); toast.success(t('Deadline added. We will remind you 30, 7 and 1 days before.')); reload(); }
    catch (err) { toast.error(err.message); }
  };
  const mark = async (d, status) => { try { await companionApi.setDeadline(d.id, status); reload(); } catch (e) { toast.error(e.message); } };
  const remove = async (d) => { if (!window.confirm(t('Delete this deadline?'))) return; try { await companionApi.deleteDeadline(d.id); reload(); } catch (e) { toast.error(e.message); } };
  const upcoming = items.filter((d) => d.status !== 'DONE');
  const done = items.filter((d) => d.status === 'DONE');
  return (
    <>
      <p className="muted" style={{ marginBottom: 12 }}>{t('Reminders go out 30, 7 and 1 days before each date, by the channels you chose in Settings. Dates marked "estimate" follow the usual cycle; confirm them with the authority.')}</p>
      {!upcoming.length ? <EmptyState icon="◷" title="No upcoming deadlines" text="Deadlines appear here when your profile needs them (tax, UIF, CIPC) or when documents in your vault expire." /> : (
        <div className="timeline">
          {upcoming.map((d) => (
            <div key={(d.id || d.documentId) + d.code + d.dueDate} className={`timeline-row ${d.daysLeft < 0 ? 'late' : d.daysLeft <= 7 ? 'soon' : ''}`}>
              <div className="timeline-date"><b>{new Date(d.dueDate).getDate()}</b><span>{monthName(new Date(d.dueDate).getMonth(), undefined, true)} {String(new Date(d.dueDate).getFullYear()).slice(2)}</span></div>
              <div className="timeline-body">
                <b>{t(d.title)}</b>
                <p>{t(d.authority)}{d.estimated ? ` · ${t('estimate - confirm the date')}` : ''}{d.kind === 'DOCUMENT' ? ` · ${t('from your document vault')}` : ''}</p>
                <Badge tone={d.daysLeft < 0 ? 'bad' : d.daysLeft <= 7 ? 'warn' : 'neutral'}>{d.daysLeft < 0 ? t('{n} days late', { n: -d.daysLeft }) : d.daysLeft === 0 ? t('Due today') : t('{n} days left', { n: d.daysLeft })}</Badge>
              </div>
              <div className="row-wrap">
                {d.sourceUrl && <a className="link-btn" href={d.sourceUrl} target="_blank" rel="noreferrer">{t('Source ↗')}</a>}
                {d.id && <button className="secondary small" onClick={() => mark(d, 'DONE')}>{t('Mark done')}</button>}
                {d.custom && <button className="link-btn" onClick={() => remove(d)}>{t('Delete')}</button>}
              </div>
            </div>
          ))}
        </div>
      )}
      <form className="fm-panel fm-form" onSubmit={add} style={{ marginTop: 18 }}>
        <h3>{t('Add your own deadline')}</h3>
        <p className="muted">{t('For example a water-use licence renewal, a GLOBALG.A.P. audit or a loan repayment.')}</p>
        <div className="form-grid">
          <label>{t('What is due')}<input className="filter" required value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} /></label>
          <label>{t('Due date')}<input className="filter" type="date" required value={form.dueDate} onChange={(e) => setForm({ ...form, dueDate: e.target.value })} /></label>
          <label>{t('Authority (optional)')}<input className="filter" value={form.authority} onChange={(e) => setForm({ ...form, authority: e.target.value })} /></label>
        </div>
        <button className="primary">{t('Add deadline')}</button>
      </form>
      {done.length > 0 && (
        <details style={{ marginTop: 16 }}><summary>{t('Done ({n})', { n: done.length })}</summary>
          {done.map((d) => <p key={d.id} className="muted">✓ {t(d.title)} · {date(d.dueDate)} <button className="link-btn" onClick={() => mark(d, 'UPCOMING')}>{t('Undo')}</button></p>)}
        </details>
      )}
    </>
  );
}

/* ------------------------------------------------------------------ business plan */

const EMPTY_CROP = { crop: '', hectares: '', yieldPerHa: '', yieldUnit: 'kg', expectedPrice: '', costPerHa: '', plantMonth: 9, harvestMonth: 1 };

function Plan({ farmer }) {
  const toast = useToast();
  const { t } = useLang();
  const [opening, setOpening] = useState('');
  const [plan, setPlan] = useState(null);
  const [editing, setEditing] = useState(null);
  const load = () => companionApi.plan(opening === '' ? undefined : opening).then(setPlan).catch((e) => toast.error(e.message));
  useEffect(() => { load(); /* eslint-disable-next-line */ }, []);

  const save = async (e) => {
    e.preventDefault();
    const num = (v) => (v === '' || v == null ? null : Number(v));
    const body = { ...editing, hectares: num(editing.hectares), yieldPerHa: num(editing.yieldPerHa), expectedPrice: num(editing.expectedPrice),
      costPerHa: num(editing.costPerHa), plantMonth: Number(editing.plantMonth), harvestMonth: Number(editing.harvestMonth) };
    try {
      if (editing.id) await companionApi.updateCrop(editing.id, body); else await companionApi.addCrop(body);
      setEditing(null); toast.success(t('Crop plan saved.')); load();
    } catch (err) { toast.error(err.message); }
  };
  const remove = async (c) => { if (!window.confirm(t('Remove {crop} from the plan?', { crop: c.crop }))) return; await companionApi.deleteCrop(c.id).catch((e) => toast.error(e.message)); load(); };

  const print = () => {
    const p = plan.projection;
    printReport(t('Farm business plan and cash-flow forecast'), [
      { title: t('Crop budget'), html: table(['Crop', 'Hectares', 'Volume', 'Revenue', 'Cost', 'Margin', 'Margin / ha', 'Break-even price'].map(tr),
        p.crops.map((b) => [b.crop, b.hectares, `${b.expectedVolume} ${b.unit}`, money(b.revenue), money(b.cost), money(b.margin), b.marginPerHa == null ? '' : money(b.marginPerHa), b.breakEvenPrice == null ? '' : money(b.breakEvenPrice)])) },
      { title: t('12-month cash flow'), html: table(['Month', 'Income', 'Crop costs', 'Overheads', 'Net', 'Cash position'].map(tr),
        p.months.map((m) => [m.month, money(m.income), money(m.cropCosts), money(m.overheads), money(m.net), money(m.cumulative)])) },
      { title: t('Assumptions'), html: `<ul>${plan.assumptions.map((a) => `<li>${t(a).replace(/</g, '&lt;')}</li>`).join('')}</ul>` }
    ], farmer.name);
  };

  if (!plan) return <SkeletonLines lines={8} />;
  const p = plan.projection;
  const chartRows = p.months.map((m) => ({ month: m.month, income: m.income, costs: Number(m.cropCosts) + Number(m.overheads) }));
  return (
    <>
      <div className="row-wrap" style={{ marginBottom: 14, justifyContent: 'space-between' }}>
        <div className="row-wrap">
          <label className="inline-label">{t('Cash in the bank today (R)')}
            <input className="filter" type="number" value={opening} onChange={(e) => setOpening(e.target.value)} onBlur={load} placeholder="0" style={{ width: 140 }} /></label>
        </div>
        <div className="row-wrap">
          <button className="secondary" onClick={() => companionApi.exportPlan(opening || undefined).catch((e) => toast.error(e.message))}>{t('Excel')}</button>
          <button className="secondary" onClick={print} disabled={!p.crops.length}>{t('Print / PDF')}</button>
          <button className="primary" onClick={() => setEditing({ ...EMPTY_CROP })}>{t('+ Add crop')}</button>
        </div>
      </div>
      {editing && (
        <form className="fm-panel fm-form" onSubmit={save}>
          <h3>{editing.id ? t('Edit {crop}', { crop: editing.crop }) : t('Add a crop to your plan')}</h3>
          <div className="form-grid">
            <label>{t('Crop')}<input className="filter" required value={editing.crop} onChange={(e) => setEditing({ ...editing, crop: e.target.value })} placeholder={t('e.g. Tomatoes')} /></label>
            <label>{t('Hectares')}<input className="filter" type="number" step="0.01" min="0.01" required value={editing.hectares} onChange={(e) => setEditing({ ...editing, hectares: e.target.value })} /></label>
            <label>{t('Expected yield per hectare')}<input className="filter" type="number" step="0.01" min="0.01" required value={editing.yieldPerHa} onChange={(e) => setEditing({ ...editing, yieldPerHa: e.target.value })} /></label>
            <label>{t('Unit')}<select className="filter" value={editing.yieldUnit} onChange={(e) => setEditing({ ...editing, yieldUnit: e.target.value })}>{['kg', 'tonne', 'crate', 'bag', 'bunch', 'head', 'dozen', 'litre'].map((u) => <option key={u} value={u}>{t(u)}</option>)}</select></label>
            <label>{t('Expected price per unit (R)')}<input className="filter" type="number" step="0.01" min="0" value={editing.expectedPrice ?? ''} onChange={(e) => setEditing({ ...editing, expectedPrice: e.target.value })} placeholder={editing.marketPrice ? t('Market: {price}', { price: money(editing.marketPrice) }) : t('Leave empty to use market price')} /></label>
            <label>{t('Cost per hectare (R)')}<input className="filter" type="number" step="0.01" min="0" value={editing.costPerHa ?? ''} onChange={(e) => setEditing({ ...editing, costPerHa: e.target.value })} placeholder={editing.actualCostPerHa ? t('Your actual: {price}', { price: money(editing.actualCostPerHa) }) : t('Seeds, fertiliser, labour…')} /></label>
            <label>{t('Planting month')}<select className="filter" value={editing.plantMonth} onChange={(e) => setEditing({ ...editing, plantMonth: e.target.value })}>{monthOptions()}</select></label>
            <label>{t('Harvest month')}<select className="filter" value={editing.harvestMonth} onChange={(e) => setEditing({ ...editing, harvestMonth: e.target.value })}>{monthOptions()}</select></label>
          </div>
          <div className="row-wrap"><button className="primary">{t('save')}</button><button type="button" className="secondary" onClick={() => setEditing(null)}>{t('cancel')}</button></div>
        </form>
      )}
      {!plan.crops.length ? (
        <EmptyState icon="❧" title="No crops in your plan yet" text="Add each crop with its area, expected yield and price. We pre-fill prices from Farm Market and costs from your statements." action="Add your first crop" onAction={() => setEditing({ ...EMPTY_CROP })} />
      ) : (
        <>
          <section className="panel">
            <div className="panel-head"><div><p className="eyebrow">{t('BUDGET')}</p><h2>{t('What each crop earns')}</h2></div></div>
            <div className="table-wrap"><table>
              <thead><tr><th>{t('Crop')}</th><th className="right">{t('Area')}</th><th className="right">{t('Revenue')}</th><th className="right">{t('Cost')}</th><th className="right">{t('Margin')}</th><th className="right">{t('Break-even price')}</th><th /></tr></thead>
              <tbody>{p.crops.map((b, i) => {
                const c = plan.crops[i];
                return (
                  <tr key={b.crop + i}>
                    <td><b>{t(b.crop)}</b><br /><span className="muted">{t('{from} to {until}', { from: monthName(c.plantMonth - 1, undefined, true), until: monthName(c.harvestMonth - 1, undefined, true) })} · {b.expectedVolume} {t(b.unit)}</span></td>
                    <td className="right">{b.hectares} ha</td>
                    <td className="right">{money(b.revenue, 0)}</td>
                    <td className="right">{money(b.cost, 0)}</td>
                    <td className={`right ${Number(b.margin) >= 0 ? 'positive' : 'negative'}`}><b>{money(b.margin, 0)}</b></td>
                    <td className="right">{b.breakEvenPrice == null ? '—' : `${money(b.breakEvenPrice)} / ${t(b.unit)}`}</td>
                    <td className="right"><button className="link-btn" onClick={() => setEditing({ ...c })}>{t('Edit')}</button> <button className="link-btn" onClick={() => remove(c)}>{t('Remove')}</button></td>
                  </tr>
                );
              })}</tbody>
            </table></div>
          </section>
          <section className="panel" style={{ marginTop: 16 }}>
            <div className="panel-head"><div><p className="eyebrow">{t('CASH FLOW')}</p><h2>{t('The next 12 months')}</h2></div>
              {Number(p.lowestCumulative) < 0 && <Badge tone="bad">{t('Cash runs short in {month}', { month: p.lowestMonth })}</Badge>}</div>
            <BarChart rows={chartRows} series={[{ key: 'income', label: 'Money in', color: '#3d9d6c' }, { key: 'costs', label: 'Money out', color: '#c98a3a' }]} />
            <div className="table-wrap"><table>
              <thead><tr><th>{t('Month')}</th><th className="right">{t('In')}</th><th className="right">{t('Out')}</th><th className="right">{t('Cash position')}</th></tr></thead>
              <tbody>{p.months.map((m) => (
                <tr key={m.month}><td>{m.month}</td><td className="right">{money(m.income, 0)}</td>
                  <td className="right">{money(Number(m.cropCosts) + Number(m.overheads), 0)}</td>
                  <td className={`right ${Number(m.cumulative) < 0 ? 'negative' : ''}`}><b>{money(m.cumulative, 0)}</b></td></tr>
              ))}</tbody>
            </table></div>
            <ul className="assumptions">{plan.assumptions.map((a) => <li key={a}>{t(a)}</li>)}</ul>
          </section>
        </>
      )}
    </>
  );
}

/* ------------------------------------------------------------------ page */

export default function Companion({ farmer, setPage }) {
  const toast = useToast();
  const { t } = useLang();
  const [tab, setTab] = useState('checklist');
  const [profile, setProfile] = useState(null);
  const [checklist, setChecklist] = useState(null);
  const [deadlines, setDeadlines] = useState(null);
  const [docs, setDocs] = useState([]);
  const [editingProfile, setEditingProfile] = useState(false);

  const loadAll = () => {
    companionApi.profile().then(setProfile).catch((e) => toast.error(e.message));
    companionApi.checklist().then(setChecklist).catch((e) => toast.error(e.message));
    companionApi.deadlines().then(setDeadlines).catch(() => setDeadlines([]));
    vaultApi.list().then(setDocs).catch(() => {});
  };
  useEffect(() => { loadAll(); /* eslint-disable-next-line */ }, []);

  const updateStep = async (code, body) => {
    try { setChecklist(await companionApi.updateStep(code, body)); if (body.status === 'DONE') toast.success(t('Step completed.')); }
    catch (e) { toast.error(e.message); }
  };

  const upcoming = useMemo(() => (deadlines || []).filter((d) => d.status === 'UPCOMING' && d.daysLeft >= 0 && d.daysLeft <= 30).length, [deadlines]);
  const needsIntake = profile && !profile.intakeCompleted;

  return (
    <>
      <div className="page-head">
        <div>
          <p className="eyebrow">{t('BUSINESS BUILDER')}</p>
          <h1>{t('From farm to formal business.')}</h1>
          <p className="muted">{t('A guided path: registration, tax, banking, workers, permits, records and a business plan, with reminders so nothing is missed.')}</p>
          {profile?.verified && <p style={{ marginTop: 8 }}><Badge tone="good">✓ Verified business</Badge></p>}
        </div>
        {!needsIntake && profile && (
          <Tabs value={tab} onChange={setTab} tabs={[['checklist', 'Checklist'], ['calendar', 'Calendar', upcoming], ['plan', 'Business plan'], ['profile', 'Farm profile']]} />
        )}
      </div>
      <div className="fm-body">
        {!profile ? <SkeletonCards count={2} /> : needsIntake || editingProfile || tab === 'profile' ? (
          <>
            {needsIntake && <div className="notice inline" style={{ marginBottom: 14 }}>{t('Answer a few questions (about 3 minutes). We use them to show only the steps that apply to your farm.')}</div>}
            <Intake initial={profile} onSaved={() => { setEditingProfile(false); setTab('checklist'); loadAll(); }} />
          </>
        ) : tab === 'checklist' ? (
          <Checklist data={checklist} docs={docs} onUpdate={updateStep} setPage={setPage} />
        ) : tab === 'calendar' ? (
          <Calendar items={deadlines} reload={() => companionApi.deadlines().then(setDeadlines)} />
        ) : (
          <Plan farmer={farmer} />
        )}
        {tab === 'profile' && !needsIntake && <p className="muted" style={{ marginTop: 10 }}>{t('Profile status:')} {titleCase(profile.legalStructure)}{profile.registrationNumber ? `, ${t('registration {n}', { n: profile.registrationNumber })}` : ''}.</p>}
      </div>
    </>
  );
}
