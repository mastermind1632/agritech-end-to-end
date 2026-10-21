import { useEffect, useState } from 'react';
import { exportApi, vaultApi } from '../lib/api';
import { Progress, ProgressRing } from '../components/Charts';
import { SkeletonCards } from '../components/Skeleton';
import EmptyState from '../components/EmptyState';
import Badge, { statusTone } from '../components/Badge';
import Tabs from '../components/Tabs';
import { useToast } from '../components/Toast';
import { date, titleCase } from '../lib/format';
import { printReport, table } from '../lib/print';
import useLang from '../hooks/useLang';
import { tr } from '../lib/i18n';

const AUTH_INFO = {
  PPECB: ['Perishable Products Export Control Board', 'Export inspection, certification and cold-chain on behalf of the Department of Agriculture.', 'https://ppecb.com'],
  DEPT_AGRICULTURE: ['Department of Agriculture', 'Plant health (phytosanitary) certificates, production unit and packhouse registration, market access.', 'https://www.dalrrd.gov.za'],
  SARS_CUSTOMS: ['SARS Customs', 'Exporter registration, export declarations and certificates of origin.', 'https://www.sars.gov.za/customs-and-excise/'],
  DESTINATION: ['Destination country', 'Import permits, pest conditions, residue limits (MRLs), labelling.', 'https://www.macmap.org'],
  PRIVATE_STANDARD: ['Buyers and private standards', 'GLOBALG.A.P., social audits and retailer standards - not law, but needed to sell.', 'https://www.globalgap.org'],
  FORWARDER: ['Freight forwarder', 'Booking, container and transport, often also customs clearing.', null],
  PLATFORM: ['AgriTech', 'Checks we can do for you from your profile and documents.', null]
};
const STATUS_LABEL = { TODO: 'To do', IN_PROGRESS: 'Busy', BLOCKED: 'Blocked', DONE: 'Done', NOT_APPLICABLE: 'N/A' };

function ItemRow({ item, docs, onUpdate, readOnly }) {
  const { t } = useLang();
  const r = item.requirement;
  const [open, setOpen] = useState(false);
  return (
    <div className={`req-row ${item.status.toLowerCase()}`}>
      <button className="req-head" onClick={() => setOpen(!open)} aria-expanded={open}>
        <span className={`step-check ${item.status === 'DONE' ? 'on' : ''}`}>{item.status === 'DONE' ? '✓' : ''}</span>
        <div>
          <b>{t(r.title)}</b>
          <span>{t(r.authorityLabel)}{r.documents ? ` · ${t(r.documents)}` : ''}</span>
        </div>
        <div className="req-badges">
          {item.changedSinceStarted && <Badge tone="warn" title="This requirement was updated after you started">Updated</Badge>}
          {r.reviewStatus !== 'VERIFIED' && <Badge tone="neutral" title="Not yet confirmed by our compliance reviewer">Draft</Badge>}
          <Badge tone={statusTone(item.status)}>{STATUS_LABEL[item.status]}</Badge>
        </div>
      </button>
      {open && (
        <div className="req-body">
          <p>{t(r.description)}</p>
          {item.autoChecked && item.status === 'DONE' && <p className="muted">✓ {t(item.note)}</p>}
          <div className="row-wrap">
            {r.sourceUrl && <a className="secondary btn-link" href={r.sourceUrl} target="_blank" rel="noreferrer">{t('Official source ↗')}</a>}
            {r.lastVerified && <span className="muted">{t('Checked {date}', { date: date(r.lastVerified) })}</span>}
          </div>
          {!readOnly && (
            <>
              <div className="row-wrap" style={{ marginTop: 10 }}>
                {Object.entries(STATUS_LABEL).map(([v, l]) => (
                  <button key={v} className={item.status === v ? 'primary small' : 'secondary small'} onClick={() => onUpdate(item, { status: v })}>{t(l)}</button>
                ))}
              </div>
              <div className="form-grid" style={{ marginTop: 10 }}>
                <label>{t('Note')}<input className="filter" defaultValue={item.note || ''} maxLength={500} onBlur={(e) => e.target.value !== (item.note || '') && onUpdate(item, { note: e.target.value })} placeholder={t('Reference number, contact person, date applied')} /></label>
                <label>{t('Document')}
                  <select className="filter" value={item.documentId || ''} onChange={(e) => onUpdate(item, { documentId: e.target.value })}>
                    <option value="">{t('None')}</option>{docs.map((d) => <option key={d.id} value={d.id}>{d.title}</option>)}
                  </select>
                </label>
              </div>
            </>
          )}
        </div>
      )}
    </div>
  );
}

function Workflow({ id, onBack, docs }) {
  const toast = useToast();
  const { t } = useLang();
  const [w, setW] = useState(null);
  const [view, setView] = useState('stages');
  const [closing, setClosing] = useState(null);
  const load = () => exportApi.get(id).then(setW).catch((e) => toast.error(e.message));
  useEffect(() => { load(); /* eslint-disable-next-line */ }, [id]);
  if (!w) return <SkeletonCards count={2} />;
  const s = w.summary;
  const active = s.status === 'ACTIVE';

  const update = async (item, patch) => {
    try { setW(await exportApi.updateItem(id, item.itemId, { status: patch.status ?? item.status, note: patch.note, documentId: patch.documentId })); }
    catch (e) { toast.error(e.message); }
  };
  const advance = async () => {
    try { setW(await exportApi.advance(id)); toast.success(t('Moved to the next stage.')); }
    catch (e) { toast.error(e.message); }
  };
  const close = async (e) => {
    e.preventDefault();
    try { setW(await exportApi.close(id, closing)); setClosing(null); toast.success(closing.cancelled ? t('Plan cancelled.') : t('Shipment recorded. Well done!')); }
    catch (err) { toast.error(err.message); }
  };
  const print = () => printReport(t('Export checklist: {product} to {destination}', { product: t(s.productName), destination: t(s.destinationName) }),
    Object.entries(w.byAuthority).map(([a, items]) => ({
      title: t('{authority} ({pct}% done)', { authority: t(AUTH_INFO[a]?.[0] || a), pct: w.authorityPercent[a] }),
      html: table(['Stage', 'Requirement', 'Documents', 'Status', 'Note'].map(tr), items.map((i) => [i.requirement.stage, t(i.requirement.title), t(i.requirement.documents || ''), t(STATUS_LABEL[i.status]), i.note || '']))
    })), t('HS code {code} · readiness {pct}%', { code: s.hsCode || '-', pct: s.readiness }));

  return (
    <>
      <button className="link-btn" onClick={onBack}>{t('← All export plans')}</button>
      <div className="fm-panel progress-hero" style={{ marginTop: 10 }}>
        <ProgressRing value={s.readiness} size={96} color="var(--gold)" />
        <div style={{ flex: 1 }}>
          <h3>{t(s.productName)} → {t(s.destinationName)}</h3>
          <p className="muted">{t('HS code {code}', { code: s.hsCode || '—' })} · {t('Stage {n} of 8: {name}', { n: s.currentStage, name: t(s.currentStageName) })}{s.targetShipDate ? ` · ${t('target ship date {date}', { date: date(s.targetShipDate) })}` : ''}</p>
          <div className="row-wrap" style={{ marginTop: 8 }}>
            {s.exportReady && <Badge tone="good">Export-ready</Badge>}
            <Badge tone={statusTone(s.status)}>{titleCase(s.status)}</Badge>
            {s.changedRequirements > 0 && <Badge tone="warn">{t('{n} requirement(s) updated', { n: s.changedRequirements })}</Badge>}
          </div>
        </div>
        <div className="row-wrap">
          <button className="secondary" onClick={print}>{t('Print / PDF')}</button>
          {active && <button className="primary" onClick={advance} disabled={!w.canAdvance}>{t('Next stage →')}</button>}
        </div>
      </div>
      {active && !w.canAdvance && w.blockers.length > 0 && (
        <div className="notice inline" style={{ marginBottom: 12 }}><b>{t('To finish this stage:')}</b> {w.blockers.map((b) => t(b)).join('; ')}.</div>
      )}
      <div className="stepper">
        {w.stages.map((st) => (
          <div key={st.stage} className={`stepper-step ${st.current ? 'current' : ''} ${st.complete ? 'complete' : ''}`}>
            <span>{st.complete ? '✓' : st.stage}</span><small>{t(st.name)}</small>
          </div>
        ))}
      </div>
      <Tabs value={view} onChange={setView} tabs={[['stages', 'By stage'], ['authority', 'By authority']]} />
      <div style={{ marginTop: 14 }}>
        {view === 'stages' ? w.stages.filter((st) => st.items.length).map((st) => (
          <section key={st.stage} className={`stage-block ${st.current ? 'current' : ''}`}>
            <div className="stage-block-head"><h3>{st.stage}. {t(st.name)}</h3><Progress value={st.percent} /></div>
            {st.items.map((i) => <ItemRow key={i.itemId} item={i} docs={docs} onUpdate={update} readOnly={!active} />)}
          </section>
        )) : Object.entries(w.byAuthority).map(([a, items]) => (
          <section key={a} className="stage-block">
            <div className="stage-block-head">
              <div><h3>{t(AUTH_INFO[a]?.[0] || a)}</h3><p className="muted">{t(AUTH_INFO[a]?.[1])} {AUTH_INFO[a]?.[2] && <a href={AUTH_INFO[a][2]} target="_blank" rel="noreferrer">{t('Website ↗')}</a>}</p></div>
              <Progress value={w.authorityPercent[a]} />
            </div>
            {items.map((i) => <ItemRow key={i.itemId} item={i} docs={docs} onUpdate={update} readOnly={!active} />)}
          </section>
        ))}
      </div>
      {active && s.currentStage === 8 && !closing && <button className="primary" onClick={() => setClosing({ outcome: '', lessons: '', cancelled: false })}>{t('Record shipment and close')}</button>}
      {active && !closing && <button className="link-btn" style={{ marginLeft: 12 }} onClick={() => setClosing({ outcome: '', lessons: '', cancelled: true })}>{t('Cancel this plan')}</button>}
      {closing && (
        <form className="fm-panel fm-form" onSubmit={close} style={{ marginTop: 14 }}>
          <h3>{closing.cancelled ? t('Cancel this export plan') : t('How did the shipment go?')}</h3>
          <label>{closing.cancelled ? t('Why are you cancelling?') : t('Outcome (arrival, price, claims)')}<textarea className="filter" rows={2} value={closing.outcome} onChange={(e) => setClosing({ ...closing, outcome: e.target.value })} /></label>
          <label>{t('Lessons for next time')}<textarea className="filter" rows={2} value={closing.lessons} onChange={(e) => setClosing({ ...closing, lessons: e.target.value })} /></label>
          <div className="row-wrap"><button className="primary">{t('save')}</button><button type="button" className="secondary" onClick={() => setClosing(null)}>{t('Back')}</button></div>
        </form>
      )}
      {(w.outcome || w.lessons) && <div className="fm-panel"><h3>{t('Outcome')}</h3><p>{w.outcome}</p>{w.lessons && <p className="muted">{t('Lessons:')} {w.lessons}</p>}</div>}
      <p className="disclaimer">{t(w.disclaimer)}</p>
    </>
  );
}

function Start({ options, onStarted }) {
  const toast = useToast();
  const { t } = useLang();
  const [f, setF] = useState({ productCode: '', destinationCode: '', targetShipDate: '', volume: '', volumeUnit: 'tonne' });
  const [preview, setPreview] = useState(null);
  useEffect(() => {
    if (!f.productCode || !f.destinationCode) { setPreview(null); return; }
    exportApi.preview(f.productCode, f.destinationCode).then(setPreview).catch(() => setPreview(null));
  }, [f.productCode, f.destinationCode]);
  const start = async (e) => {
    e.preventDefault();
    try {
      const w = await exportApi.start({ ...f, volume: f.volume === '' ? null : Number(f.volume), targetShipDate: f.targetShipDate || null });
      toast.success(t('Export plan created. Stage 1 was checked against your Business Builder profile.'));
      onStarted(w.summary.id);
    } catch (err) { toast.error(err.message); }
  };
  const count = preview ? Object.values(preview).reduce((a, l) => a + l.length, 0) : 0;
  return (
    <form className="fm-panel fm-form" onSubmit={start}>
      <h3>{t('Check what exporting would take')}</h3>
      <p className="muted">{t('Choose a product and a market. You will see every requirement, grouped by the authority it comes from, before you commit.')}</p>
      <div className="form-grid">
        <label>{t('Product')}<select className="filter" required value={f.productCode} onChange={(e) => setF({ ...f, productCode: e.target.value })}>
          <option value="">{t('Choose')}</option>{options.products.map((p) => <option key={p.code} value={p.code}>{t(p.name)} (HS {p.hsCode})</option>)}</select></label>
        <label>{t('Destination')}<select className="filter" required value={f.destinationCode} onChange={(e) => setF({ ...f, destinationCode: e.target.value })}>
          <option value="">{t('Choose')}</option>{options.destinations.map((d) => <option key={d.code} value={d.code}>{t(d.name)}</option>)}</select></label>
        <label>{t('Target ship date (optional)')}<input className="filter" type="date" value={f.targetShipDate} onChange={(e) => setF({ ...f, targetShipDate: e.target.value })} /></label>
        <label>{t('Volume (optional)')}<input className="filter" type="number" min="0" step="0.1" value={f.volume} onChange={(e) => setF({ ...f, volume: e.target.value })} /></label>
      </div>
      {preview && (
        <div className="preview-grid">
          {Object.entries(preview).map(([a, list]) => (
            <div key={a} className="preview-card"><b>{t(AUTH_INFO[a]?.[0] || a)}</b><span>{t(list.length > 1 ? '{n} requirements' : '{n} requirement', { n: list.length })}</span>
              <ul>{list.slice(0, 4).map((r) => <li key={r.id}>{t(r.title)}</li>)}{list.length > 4 && <li>{t('…and {n} more', { n: list.length - 4 })}</li>}</ul></div>
          ))}
        </div>
      )}
      <button className="primary" disabled={!preview}>{preview ? t('Start plan with {n} requirements', { n: count }) : t('Start export plan')}</button>
    </form>
  );
}

export default function ExportReadiness() {
  const toast = useToast();
  const { t } = useLang();
  const [options, setOptions] = useState(null);
  const [plans, setPlans] = useState(null);
  const [openId, setOpenId] = useState(null);
  const [docs, setDocs] = useState([]);
  const [tab, setTab] = useState('plans');
  const load = () => exportApi.list().then(setPlans).catch((e) => { toast.error(e.message); setPlans([]); });
  useEffect(() => {
    exportApi.options().then(setOptions).catch((e) => toast.error(e.message));
    vaultApi.list().then(setDocs).catch(() => {});
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <>
      <div className="page-head">
        <div>
          <p className="eyebrow">{t('EXPORT READINESS · PPECB')}</p>
          <h1>{t('From "interested" to "shipment cleared".')}</h1>
          <p className="muted">{t('Every requirement shows the authority it comes from - PPECB, the Department of Agriculture, SARS Customs, the destination country or your buyer - so you know whom to call when a step is blocked.')}</p>
        </div>
        {!openId && <Tabs value={tab} onChange={setTab} tabs={[['plans', 'My export plans'], ['new', 'Start a plan'], ['who', 'Who does what']]} />}
      </div>
      <div className="fm-body">
        {openId ? <Workflow id={openId} docs={docs} onBack={() => { setOpenId(null); load(); }} />
          : tab === 'new' ? (options ? <Start options={options} onStarted={(id) => { setOpenId(id); setTab('plans'); }} /> : <SkeletonCards count={1} />)
          : tab === 'who' ? (
            <div className="card-grid">
              {Object.entries(AUTH_INFO).filter(([k]) => k !== 'PLATFORM').map(([k, [name, text, url]]) => (
                <div key={k} className="panel"><h3>{t(name)}</h3><p className="muted">{t(text)}</p>{url && <a href={url} target="_blank" rel="noreferrer">{url.replace('https://', '')} ↗</a>}</div>
              ))}
              <div className="panel"><h3>{t('Useful trade tools')}</h3><ul className="link-list">
                <li><a href="https://www.macmap.org" target="_blank" rel="noreferrer">ITC Market Access Map</a> - {t('tariffs and requirements')}</li>
                <li><a href="https://www.trademap.org" target="_blank" rel="noreferrer">ITC Trade Map</a> - {t('demand and trade flows')}</li>
                <li><a href="https://trade.ec.europa.eu/access-to-markets" target="_blank" rel="noreferrer">EU Access2Markets</a></li>
                <li><a href="https://www.ippc.int" target="_blank" rel="noreferrer">IPPC</a> - {t('plant health standards')}</li>
                <li><a href="https://au-afcfta.org" target="_blank" rel="noreferrer">AfCFTA Secretariat</a></li>
                <li><a href="https://www.fpef.co.za" target="_blank" rel="noreferrer">Fresh Produce Exporters' Forum</a></li>
              </ul></div>
            </div>
          ) : !plans ? <SkeletonCards count={2} /> : !plans.length ? (
            <EmptyState icon="✈" title="No export plans yet" text="Pick a product and a market to see the full list of registrations, inspections and documents you need." action="Start a plan" onAction={() => setTab('new')} />
          ) : (
            <div className="card-grid">
              {plans.map((p) => (
                <button key={p.id} className="panel plan-card" onClick={() => setOpenId(p.id)}>
                  <div className="row-wrap" style={{ justifyContent: 'space-between' }}>
                    <Badge tone={statusTone(p.status)}>{titleCase(p.status)}</Badge>{p.exportReady && <Badge tone="good">Export-ready</Badge>}
                  </div>
                  <h3>{t(p.productName)} → {t(p.destinationName)}</h3>
                  <p className="muted">{t('Stage {n}: {name}', { n: p.currentStage, name: t(p.currentStageName) })}</p>
                  <Progress value={p.readiness} label="Readiness" />
                  {p.changedRequirements > 0 && <p className="warn-text">{t('{n} requirement(s) changed - review', { n: p.changedRequirements })}</p>}
                </button>
              ))}
            </div>
          )}
      </div>
    </>
  );
}
