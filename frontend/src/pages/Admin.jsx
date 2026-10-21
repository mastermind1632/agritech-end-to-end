import { useEffect, useState } from 'react';
import { adminApi, vaultApi } from '../lib/api';
import { SkeletonCards, SkeletonLines } from '../components/Skeleton';
import EmptyState from '../components/EmptyState';
import Badge, { statusTone } from '../components/Badge';
import Tabs from '../components/Tabs';
import { BarList } from '../components/Charts';
import { useToast } from '../components/Toast';
import { DisputeDetail } from './Disputes';
import { date, dateTime, money, timeAgo, titleCase } from '../lib/format';
import useLang from '../hooks/useLang';

const ROLES = ['FARMER', 'BUYER', 'SUPPLIER', 'ADMIN'];

/* ---------------------------------------------------------------- overview */

function Overview({ stats, go }) {
  const { t } = useLang();
  if (!stats) return <SkeletonCards count={4} />;
  const s = stats;
  const users = Object.entries(s.users || {}).map(([role, count]) => ({ label: titleCase(role), amount: count }));
  const orders = Object.entries(s.ordersByStatus || {}).map(([st, count]) => ({ label: titleCase(st), amount: count }));
  const todo = [
    ['disputes', s.openDisputes, 'open disputes waiting for a decision'],
    ['verify', s.pendingVerifications, 'businesses waiting for verification'],
    ['content', s.pendingContentChanges, 'regulatory changes waiting for review'],
    ['privacy', s.openDataRequests, 'POPIA deletion requests']
  ].filter(([, n]) => n > 0);
  return (
    <>
      <section className="stats" style={{ padding: 0 }}>
        <div className="stat-card"><span>{t('Users')}</span><strong>{s.totalUsers}</strong><small>{t('{n} online now', { n: s.liveConnections })}</small></div>
        <div className="stat-card"><span>{t('Completed sales')}</span><strong>{money(s.completedSalesValue, 0)}</strong><small>{t('Farm Market value')}</small></div>
        <div className="stat-card"><span>{t('Dispute rate')}</span><strong>{s.disputeRate}%</strong><small>{t('Of completed and disputed orders')}</small></div>
        <div className="stat-card"><span>{t('Active listings')}</span><strong>{s.activeListings}</strong><small>{t('{n} open group orders', { n: s.openGroupOrders })}</small></div>
        <div className="stat-card"><span>{t('Business profiles')}</span><strong>{s.businessProfiles}</strong><small>{t('{n} verified', { n: s.verifiedBusinesses })}</small></div>
        <div className="stat-card"><span>{t('Export plans')}</span><strong>{s.activeExportPlans}</strong><small>{t('{n} shipped', { n: s.shippedExports })}</small></div>
        <div className="stat-card"><span>{t('Statements')}</span><strong>{s.statementsConfirmed}</strong><small>{t('{n} in review', { n: s.statementsInReview })}</small></div>
      </section>
      <div className="dashboard-grid flush" style={{ marginTop: 16 }}>
        <section className="panel">
          <div className="panel-head"><div><p className="eyebrow">{t('NEEDS YOU')}</p><h2>{t('Admin to-do')}</h2></div></div>
          {todo.length ? todo.map(([tab, n, text]) => (
            <button key={tab} className="list-row as-button" onClick={() => go(tab)}><span className="round-icon">{n}</span><div><b>{n} {t(text)}</b></div></button>
          )) : <p className="muted">{t('Nothing waiting.')} 🎉</p>}
        </section>
        <section className="panel">
          <div className="panel-head"><div><p className="eyebrow">{t('PLATFORM')}</p><h2>{t('Users and orders')}</h2></div></div>
          <BarList items={users} format={(v) => v} />
          <div style={{ height: 12 }} />
          <BarList items={orders} format={(v) => v} color="var(--gold, #c98a3a)" />
        </section>
      </div>
    </>
  );
}

/* ---------------------------------------------------------------- users */

function Users() {
  const toast = useToast();
  const { t } = useLang();
  const [q, setQ] = useState('');
  const [role, setRole] = useState('');
  const [page, setPage] = useState(0);
  const [data, setData] = useState(null);
  const load = () => adminApi.users({ q: q || undefined, role: role || undefined, page, size: 25 }).then(setData).catch((e) => toast.error(e.message));
  useEffect(() => { load(); /* eslint-disable-next-line */ }, [role, page]);
  const act = async (fn, msg) => { try { await fn(); toast.success(t(msg)); load(); } catch (e) { toast.error(e.message); } };
  return (
    <>
      <form className="row-wrap" onSubmit={(e) => { e.preventDefault(); setPage(0); load(); }} style={{ marginBottom: 12 }}>
        <input className="filter" placeholder={t('Search name, phone or email')} value={q} onChange={(e) => setQ(e.target.value)} />
        <select className="filter" value={role} onChange={(e) => { setRole(e.target.value); setPage(0); }}>
          <option value="">{t('All roles')}</option>{ROLES.map((r) => <option key={r} value={r}>{titleCase(r)}</option>)}
        </select>
        <button className="secondary">{t('search')}</button>
      </form>
      {!data ? <SkeletonLines lines={8} /> : (
        <div className="table-wrap panel" style={{ padding: 0 }}>
          <table>
            <thead><tr><th>{t('Name')}</th><th>{t('Contact')}</th><th>{t('Role')}</th><th>{t('Status')}</th><th>{t('Last sign-in')}</th><th /></tr></thead>
            <tbody>{data.items.map((u) => (
              <tr key={u.id}>
                <td><b>{u.name}</b><br /><small className="muted">{u.location} · {t('joined {date}', { date: date(u.createdAt) })}{u.mfaEnabled ? ` · ${t('2FA on')}` : ''}</small></td>
                <td>{u.contact}<br /><small className="muted">{u.email}</small></td>
                <td><select className="filter compact" value={u.role} onChange={(e) => window.confirm(t('Change the role of {name} to {role}?', { name: u.name, role: e.target.value }))
                  && act(() => adminApi.setRole(u.id, e.target.value), 'Role changed.')}>{ROLES.map((r) => <option key={r}>{r}</option>)}</select></td>
                <td><Badge tone={u.status === 'ACTIVE' ? 'good' : 'bad'}>{titleCase(u.status)}</Badge></td>
                <td>{u.lastLoginAt ? timeAgo(u.lastLoginAt) : <span className="muted">{t('never')}</span>}</td>
                <td>{u.status === 'ACTIVE'
                  ? <button className="link-btn danger" onClick={() => window.confirm(t('Suspend {name}? They are signed out everywhere.', { name: u.name })) && act(() => adminApi.setStatus(u.id, 'SUSPENDED'), 'User suspended.')}>{t('Suspend')}</button>
                  : <button className="link-btn" onClick={() => act(() => adminApi.setStatus(u.id, 'ACTIVE'), 'User reactivated.')}>{t('Reactivate')}</button>}</td>
              </tr>
            ))}</tbody>
          </table>
          <Pager data={data} page={page} setPage={setPage} />
        </div>
      )}
    </>
  );
}

function Pager({ data, page, setPage }) {
  const { t } = useLang();
  return (
    <div className="row-wrap pager">
      <span className="muted">{t('{n} total', { n: data.total })}</span>
      <button className="secondary small" disabled={page === 0} onClick={() => setPage(page - 1)}>{t('Previous')}</button>
      <span>{t('Page {n}', { n: page + 1 })}</span>
      <button className="secondary small" disabled={!data.hasMore} onClick={() => setPage(page + 1)}>{t('Next')}</button>
    </div>
  );
}

/* ---------------------------------------------------------------- disputes */

function AdminDisputes() {
  const toast = useToast();
  const { t } = useLang();
  const [openOnly, setOpenOnly] = useState(true);
  const [list, setList] = useState(null);
  const load = () => adminApi.disputes(openOnly).then(setList).catch((e) => { toast.error(e.message); setList([]); });
  useEffect(() => { load(); /* eslint-disable-next-line */ }, [openOnly]);
  return (
    <>
      <label className="check inline" style={{ marginBottom: 12 }}><input type="checkbox" checked={openOnly} onChange={(e) => setOpenOnly(e.target.checked)} /><span>{t('Open disputes only')}</span></label>
      {!list ? <SkeletonCards count={2} /> : !list.length ? <EmptyState icon="⚖" title="No disputes" text="Nothing to decide right now." />
        : <div className="stack">{list.map((d) => <DisputeDetail key={d.id} dispute={d} onChanged={load} />)}</div>}
    </>
  );
}

/* ---------------------------------------------------------------- verifications */

function Verifications() {
  const toast = useToast();
  const { t } = useLang();
  const [list, setList] = useState(null);
  const [notes, setNotes] = useState({});
  const load = () => adminApi.verifications().then(setList).catch((e) => { toast.error(e.message); setList([]); });
  useEffect(() => { load(); /* eslint-disable-next-line */ }, []);
  const decide = async (v, approve) => {
    try { await adminApi.verify(v.farmerId, approve, notes[v.farmerId] || ''); toast.success(approve ? t('Business verified. The farmer now shows a verified badge.') : t('Sent back to the farmer.')); load(); }
    catch (e) { toast.error(e.message); }
  };
  if (!list) return <SkeletonCards count={2} />;
  if (!list.length) return <EmptyState icon="✓" title="No businesses waiting" text="Farmers who finish the registration steps in Business Builder and upload their CIPC certificate appear here." />;
  return (
    <div className="card-grid">
      {list.map((v) => (
        <article key={v.farmerId} className="panel">
          <h3>{v.businessName || v.farmerName}</h3>
          <p className="muted">{v.farmerName} · {titleCase(v.legalStructure || 'unknown structure')} · {t('updated {when}', { when: timeAgo(v.updatedAt) })}</p>
          <p>{t('CIPC number:')} <b>{v.registrationNumber || '—'}</b><br />{t('Tax number:')} <b>{v.taxNumber || '—'}</b></p>
          <ul className="link-list">
            {v.documents.map((d) => <li key={d.id}><button className="link-btn" onClick={() => vaultApi.open(d.id).catch((e) => toast.error(e.message))}>{d.title}</button> <span className="muted">({titleCase(d.docType)})</span></li>)}
            {!v.documents.length && <li className="muted">{t('No documents uploaded')}</li>}
          </ul>
          <input className="filter" placeholder={t('Note to the farmer (optional)')} value={notes[v.farmerId] || ''} onChange={(e) => setNotes({ ...notes, [v.farmerId]: e.target.value })} />
          <div className="row-wrap" style={{ marginTop: 8 }}>
            <button className="primary small" onClick={() => decide(v, true)}>{t('Verify')}</button>
            <button className="secondary small" onClick={() => decide(v, false)}>{t('Needs more information')}</button>
          </div>
        </article>
      ))}
    </div>
  );
}

/* ---------------------------------------------------------------- content (companion steps + export rules) */

function ReviewBadge({ status, lastVerified }) {
  const { t } = useLang();
  return <Badge tone={status === 'VERIFIED' ? 'good' : 'warn'} title={lastVerified ? t('Last verified {date}', { date: date(lastVerified) }) : t('Not yet checked by an expert')}>
    {status === 'VERIFIED' ? t('Verified {date}', { date: date(lastVerified) }) : t('Draft - needs expert review')}</Badge>;
}

function StepEditor({ step, onSaved }) {
  const toast = useToast();
  const { t } = useLang();
  const [f, setF] = useState({ title: step.title, description: step.description, why: step.why || '', authority: step.authority || '', linkUrl: step.linkUrl || '', linkLabel: step.linkLabel || '', active: step.active });
  const save = async () => { try { await adminApi.editStep(step.code, f); toast.success(t('Step saved. It is marked as draft until verified.')); onSaved(); } catch (e) { toast.error(e.message); } };
  return (
    <div className="form-grid editor">
      <label>{t('Title')}<input value={f.title} onChange={(e) => setF({ ...f, title: e.target.value })} /></label>
      <label>{t('Authority')}<input value={f.authority} onChange={(e) => setF({ ...f, authority: e.target.value })} /></label>
      <label className="span-all">{t('What to do')}<textarea rows={3} value={f.description} onChange={(e) => setF({ ...f, description: e.target.value })} /></label>
      <label className="span-all">{t('Why it matters')}<textarea rows={2} value={f.why} onChange={(e) => setF({ ...f, why: e.target.value })} /></label>
      <label>{t('Official link')}<input value={f.linkUrl} onChange={(e) => setF({ ...f, linkUrl: e.target.value })} /></label>
      <label>{t('Link text')}<input value={f.linkLabel} onChange={(e) => setF({ ...f, linkLabel: e.target.value })} /></label>
      <label className="check"><input type="checkbox" checked={f.active} onChange={(e) => setF({ ...f, active: e.target.checked })} /><span>{t('Shown to farmers')}</span></label>
      <div className="form-actions"><button className="primary small" onClick={save}>{t('save')}</button></div>
    </div>
  );
}

function RequirementEditor({ req, onSaved }) {
  const toast = useToast();
  const { t } = useLang();
  const [f, setF] = useState({ title: req.title, description: req.description, documents: req.documents || '', leadTimeDays: req.leadTimeDays ?? '', estCost: req.estCost || '', sourceUrl: req.sourceUrl || '', changeSummary: '' });
  const save = async () => {
    try {
      await adminApi.editRequirement(req.id, { ...f, leadTimeDays: f.leadTimeDays === '' ? null : Number(f.leadTimeDays) });
      toast.success(t('Requirement saved. Farmers with active export plans that use it are notified.')); onSaved();
    } catch (e) { toast.error(e.message); }
  };
  return (
    <div className="form-grid editor">
      <label className="span-all">{t('Title')}<input value={f.title} onChange={(e) => setF({ ...f, title: e.target.value })} /></label>
      <label className="span-all">{t('Description')}<textarea rows={3} value={f.description} onChange={(e) => setF({ ...f, description: e.target.value })} /></label>
      <label className="span-all">{t('Documents needed')}<input value={f.documents} onChange={(e) => setF({ ...f, documents: e.target.value })} /></label>
      <label>{t('Lead time (days)')}<input type="number" min="0" value={f.leadTimeDays} onChange={(e) => setF({ ...f, leadTimeDays: e.target.value })} /></label>
      <label>{t('Estimated cost')}<input value={f.estCost} onChange={(e) => setF({ ...f, estCost: e.target.value })} /></label>
      <label className="span-all">{t('Official source')}<input value={f.sourceUrl} onChange={(e) => setF({ ...f, sourceUrl: e.target.value })} /></label>
      <label className="span-all">{t('What changed (sent to affected farmers)')}<input value={f.changeSummary} onChange={(e) => setF({ ...f, changeSummary: e.target.value })} placeholder={t('e.g. PPECB inspection fee updated for 2027')} /></label>
      <div className="form-actions"><button className="primary small" onClick={save}>{t('Save new version')}</button></div>
    </div>
  );
}

function Content() {
  const toast = useToast();
  const { t } = useLang();
  const [view, setView] = useState('changes');
  const [steps, setSteps] = useState(null);
  const [reqs, setReqs] = useState(null);
  const [changes, setChanges] = useState(null);
  const [editing, setEditing] = useState(null);
  const [draftOnly, setDraftOnly] = useState(true);
  const [proposal, setProposal] = useState({ requirementId: '', summary: '' });
  const load = () => {
    adminApi.steps().then(setSteps).catch((e) => toast.error(e.message));
    adminApi.requirements().then(setReqs).catch((e) => toast.error(e.message));
    adminApi.changes().then(setChanges).catch((e) => toast.error(e.message));
  };
  useEffect(() => { load(); /* eslint-disable-next-line */ }, []);
  const act = async (fn, msg) => { try { const r = await fn(); toast.success(typeof msg === 'function' ? msg(r) : t(msg)); load(); } catch (e) { toast.error(e.message); } };
  const draftSteps = (steps || []).filter((s) => !draftOnly || s.reviewStatus !== 'VERIFIED');
  const draftReqs = (reqs || []).filter((r) => !draftOnly || r.reviewStatus !== 'VERIFIED');

  return (
    <>
      <div className="notice inline">{t('Regulatory content is seeded as DRAFT. It must be checked by someone qualified (agricultural economist, export agent, tax practitioner) against the official source and marked verified. Farmers see the review status next to each item.')}</div>
      <div className="row-wrap" style={{ justifyContent: 'space-between', margin: '12px 0' }}>
        <div className="segmented">
          <button className={view === 'changes' ? 'on' : ''} onClick={() => setView('changes')}>{t('Change alerts ({n})', { n: changes?.length || 0 })}</button>
          <button className={view === 'export' ? 'on' : ''} onClick={() => setView('export')}>{t('Export requirements ({n})', { n: reqs?.length || 0 })}</button>
          <button className={view === 'steps' ? 'on' : ''} onClick={() => setView('steps')}>{t('Business Builder steps ({n})', { n: steps?.length || 0 })}</button>
        </div>
        {view !== 'changes' && <label className="check inline"><input type="checkbox" checked={draftOnly} onChange={(e) => setDraftOnly(e.target.checked)} /><span>{t('Unverified only')}</span></label>}
      </div>

      {view === 'changes' && (
        <>
          <div className="row-wrap" style={{ marginBottom: 12 }}>
            <button className="secondary small" onClick={() => act(() => adminApi.runMonitor(), (r) => t('Checked official sources: {n} change(s) detected.', { n: r.changesDetected }))}>{t('Check official sources now')}</button>
            <form className="row-wrap" onSubmit={(e) => { e.preventDefault(); act(() => adminApi.proposeChange({ requirementId: Number(proposal.requirementId), summary: proposal.summary }), 'Change logged for review.'); setProposal({ requirementId: '', summary: '' }); }}>
              <select className="filter compact" value={proposal.requirementId} onChange={(e) => setProposal({ ...proposal, requirementId: e.target.value })} required>
                <option value="">{t('Log a change for…')}</option>{(reqs || []).map((r) => <option key={r.id} value={r.id}>{r.code} · {r.title}</option>)}
              </select>
              <input className="filter compact" placeholder={t('What changed?')} value={proposal.summary} onChange={(e) => setProposal({ ...proposal, summary: e.target.value })} required />
              <button className="secondary small">{t('Log')}</button>
            </form>
          </div>
          {!changes ? <SkeletonLines lines={4} /> : !changes.length ? <EmptyState icon="✓" title="No changes waiting" text="When an official source page changes, or someone logs a change, it waits here for a human to approve before farmers are told." /> : changes.map((c) => (
            <div key={c.id} className="list-row">
              <span className="round-icon">!</span>
              <div style={{ flex: 1 }}>
                <b>{c.requirementTitle}</b> <span className="muted">· {titleCase(c.source)} · {timeAgo(c.createdAt)}</span>
                <p>{c.summary}</p>
                {c.proposedTitle && <p className="muted">{t('Proposed title:')} {c.proposedTitle}</p>}
              </div>
              <button className="primary small" onClick={() => act(() => adminApi.review(c.id, true), 'Approved. Affected farmers are notified.')}>{t('Approve and notify')}</button>
              <button className="link-btn" onClick={() => act(() => adminApi.review(c.id, false), 'Dismissed.')}>{t('Dismiss')}</button>
            </div>
          ))}
        </>
      )}

      {view === 'export' && (!reqs ? <SkeletonLines lines={8} /> : draftReqs.map((r) => (
        <div key={r.id} className="panel content-row">
          <div className="row-wrap" style={{ justifyContent: 'space-between' }}>
            <div><p className="eyebrow">{r.code} · {r.stageName} · {r.authorityLabel}{r.productCode ? ` · ${r.productCode}` : ''}{r.destinationCode ? ` → ${r.destinationCode}` : ''} · v{r.version}</p><b>{r.title}</b></div>
            <div className="row-wrap">
              <ReviewBadge status={r.reviewStatus} lastVerified={r.lastVerified} />
              <button className="link-btn" onClick={() => setEditing(editing === `r${r.id}` ? null : `r${r.id}`)}>{editing === `r${r.id}` ? t('Close') : t('Edit')}</button>
              <button className="secondary small" onClick={() => act(() => adminApi.verifyRequirement(r.id), 'Marked as verified today.')}>{t('Mark verified')}</button>
            </div>
          </div>
          {editing === `r${r.id}` ? <RequirementEditor req={r} onSaved={() => { setEditing(null); load(); }} /> : <p className="muted">{r.description}</p>}
          {r.sourceUrl && <a className="source-note" href={r.sourceUrl} target="_blank" rel="noreferrer">{t('Source ↗')}</a>}
        </div>
      )))}

      {view === 'steps' && (!steps ? <SkeletonLines lines={8} /> : draftSteps.map((s) => (
        <div key={s.code} className="panel content-row">
          <div className="row-wrap" style={{ justifyContent: 'space-between' }}>
            <div><p className="eyebrow">{s.code} · {t('Stage {n}: {name}', { n: s.stage, name: s.stageName })} · {s.authority} · {t('shown when {rule}', { rule: s.ruleKey })}{s.active ? '' : ` · ${t('HIDDEN')}`}</p><b>{s.title}</b></div>
            <div className="row-wrap">
              <ReviewBadge status={s.reviewStatus} lastVerified={s.lastVerified} />
              <button className="link-btn" onClick={() => setEditing(editing === s.code ? null : s.code)}>{editing === s.code ? t('Close') : t('Edit')}</button>
              <button className="secondary small" onClick={() => act(() => adminApi.verifyStep(s.code), 'Marked as verified today.')}>{t('Mark verified')}</button>
            </div>
          </div>
          {editing === s.code ? <StepEditor step={s} onSaved={() => { setEditing(null); load(); }} /> : <p className="muted">{s.description}</p>}
        </div>
      )))}
    </>
  );
}

/* ---------------------------------------------------------------- listings, audit, privacy */

function Listings() {
  const toast = useToast();
  const { t } = useLang();
  const [status, setStatus] = useState('ACTIVE');
  const [page, setPage] = useState(0);
  const [data, setData] = useState(null);
  const load = () => adminApi.listings({ status: status || undefined, page, size: 25 }).then(setData).catch((e) => toast.error(e.message));
  useEffect(() => { load(); /* eslint-disable-next-line */ }, [status, page]);
  const remove = async (l) => {
    const reason = window.prompt(t('Why is "{product}" being removed? The seller sees this reason.', { product: l.product }));
    if (!reason) return;
    try { await adminApi.removeListing(l.id, reason); toast.success(t('Listing removed and seller notified.')); load(); } catch (e) { toast.error(e.message); }
  };
  return (
    <>
      <select className="filter" value={status} onChange={(e) => { setStatus(e.target.value); setPage(0); }} style={{ marginBottom: 12 }}>
        {['ACTIVE', 'SOLD_OUT', 'REMOVED', ''].map((s) => <option key={s} value={s}>{s ? titleCase(s) : t('All')}</option>)}
      </select>
      {!data ? <SkeletonLines lines={6} /> : (
        <div className="table-wrap panel" style={{ padding: 0 }}>
          <table>
            <thead><tr><th>{t('Product')}</th><th>{t('Seller')}</th><th className="right">{t('Price')}</th><th className="right">{t('Available')}</th><th>{t('Status')}</th><th>{t('Listed')}</th><th /></tr></thead>
            <tbody>{data.items.map((l) => (
              <tr key={l.id}><td><b>{l.product}</b></td><td>{l.sellerName}</td><td className="right">{money(l.pricePerUnit)} / {l.unit}</td>
                <td className="right">{Number(l.quantityAvailable)} {l.unit}</td><td><Badge tone={statusTone(l.status)}>{titleCase(l.status)}</Badge></td>
                <td>{date(l.createdAt)}</td>
                <td>{l.status !== 'REMOVED' && <button className="link-btn danger" onClick={() => remove(l)}>{t('Remove')}</button>}</td></tr>
            ))}</tbody>
          </table>
          <Pager data={data} page={page} setPage={setPage} />
        </div>
      )}
    </>
  );
}

function Audit() {
  const toast = useToast();
  const { t } = useLang();
  const [filters, setFilters] = useState({ actor: '', entityType: '' });
  const [page, setPage] = useState(0);
  const [data, setData] = useState(null);
  const load = () => adminApi.audit({ actor: filters.actor || undefined, entityType: filters.entityType || undefined, page, size: 50 }).then(setData).catch((e) => toast.error(e.message));
  useEffect(() => { load(); /* eslint-disable-next-line */ }, [page]);
  return (
    <>
      <form className="row-wrap" onSubmit={(e) => { e.preventDefault(); setPage(0); load(); }} style={{ marginBottom: 12 }}>
        <input className="filter" placeholder={t('User id')} value={filters.actor} onChange={(e) => setFilters({ ...filters, actor: e.target.value })} />
        <input className="filter" placeholder={t('Entity type (e.g. ORDER, DISPUTE, VAULT)')} value={filters.entityType} onChange={(e) => setFilters({ ...filters, entityType: e.target.value.toUpperCase() })} />
        <button className="secondary">{t('Filter')}</button>
      </form>
      <p className="muted">{t('The audit log is append-only: it records sign-ins, money-related changes, document access, role changes and admin decisions.')}</p>
      {!data ? <SkeletonLines lines={10} /> : (
        <div className="table-wrap panel" style={{ padding: 0 }}>
          <table className="audit">
            <thead><tr><th>{t('When')}</th><th>{t('Action')}</th><th>{t('Entity')}</th><th>{t('Who')}</th><th>{t('Detail')}</th></tr></thead>
            <tbody>{data.items.map((a) => (
              <tr key={a.id}><td>{dateTime(a.createdAt)}</td><td><code>{a.action}</code></td><td>{a.entityType} {a.entityId}</td>
                <td><small>{a.actorId}</small>{a.ip && <><br /><small className="muted">{a.ip}</small></>}</td><td><small>{a.detail}</small></td></tr>
            ))}</tbody>
          </table>
          <Pager data={data} page={page} setPage={setPage} />
        </div>
      )}
    </>
  );
}

function Privacy() {
  const toast = useToast();
  const { t } = useLang();
  const [openOnly, setOpenOnly] = useState(true);
  const [list, setList] = useState(null);
  const load = () => adminApi.dataRequests(openOnly).then(setList).catch((e) => { toast.error(e.message); setList([]); });
  useEffect(() => { load(); /* eslint-disable-next-line */ }, [openOnly]);
  const complete = async (r) => {
    if (!window.confirm(t('Anonymise the account of {name}? Personal details are removed and they can no longer sign in. Orders stay (without personal details) for the other party and tax records. This cannot be undone.', { name: r.farmerName }))) return;
    try { await adminApi.completeDeletion(r.id); toast.success(t('Account anonymised.')); load(); } catch (e) { toast.error(e.message); }
  };
  return (
    <>
      <label className="check inline" style={{ marginBottom: 12 }}><input type="checkbox" checked={openOnly} onChange={(e) => setOpenOnly(e.target.checked)} /><span>{t('Open requests only')}</span></label>
      {!list ? <SkeletonLines lines={4} /> : !list.length ? <EmptyState icon="✓" title="No requests" text="POPIA deletion requests from users appear here." /> : list.map((r) => (
        <div key={r.id} className="list-row">
          <span className="round-icon">⌫</span>
          <div style={{ flex: 1 }}><b>{r.farmerName}</b> <span className="muted">· {titleCase(r.type)} · {timeAgo(r.createdAt)}</span>
            {r.reason && <p>{r.reason}</p>}{r.handledAt && <p className="muted">{t('Handled {when}', { when: dateTime(r.handledAt) })}</p>}</div>
          <Badge tone={statusTone(r.status)}>{titleCase(r.status)}</Badge>
          {r.status === 'OPEN' && <button className="primary small" onClick={() => complete(r)}>{t('Anonymise account')}</button>}
        </div>
      ))}
    </>
  );
}

/* ---------------------------------------------------------------- page */

export default function Admin() {
  const toast = useToast();
  const { t } = useLang();
  const [tab, setTab] = useState('overview');
  const [stats, setStats] = useState(null);
  useEffect(() => { adminApi.stats().then(setStats).catch((e) => toast.error(e.message)); /* eslint-disable-next-line */ }, [tab]);
  const s = stats || {};
  return (
    <>
      <div className="page-head">
        <div>
          <p className="eyebrow">{t('ADMINISTRATION')}</p>
          <h1>{t('Run the platform.')}</h1>
          <p className="muted">{t('Users and roles, disputes, business verification, regulatory content, moderation, the audit trail and POPIA requests.')}</p>
        </div>
      </div>
      <div className="fm-body">
        <Tabs value={tab} onChange={setTab} tabs={[
          ['overview', 'Overview'], ['users', 'Users'], ['disputes', 'Disputes', s.openDisputes], ['verify', 'Verification', s.pendingVerifications],
          ['content', 'Content', s.pendingContentChanges], ['listings', 'Listings'], ['audit', 'Audit log'], ['privacy', 'POPIA', s.openDataRequests]
        ]} />
        <div style={{ marginTop: 16 }}>
          {tab === 'overview' && <Overview stats={stats} go={setTab} />}
          {tab === 'users' && <Users />}
          {tab === 'disputes' && <AdminDisputes />}
          {tab === 'verify' && <Verifications />}
          {tab === 'content' && <Content />}
          {tab === 'listings' && <Listings />}
          {tab === 'audit' && <Audit />}
          {tab === 'privacy' && <Privacy />}
        </div>
      </div>
    </>
  );
}
