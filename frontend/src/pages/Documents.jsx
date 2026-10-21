import { useEffect, useMemo, useRef, useState } from 'react';
import { vaultApi } from '../lib/api';
import { SkeletonCards } from '../components/Skeleton';
import EmptyState from '../components/EmptyState';
import Badge from '../components/Badge';
import { useToast } from '../components/Toast';
import { bytes, date, titleCase } from '../lib/format';
import useLang from '../hooks/useLang';

const EXPIRY = {
  EXPIRED: ['bad', 'Expired'],
  EXPIRING: ['warn', 'Expires soon'],
  VALID: ['good', 'Valid'],
  NONE: ['neutral', 'No expiry']
};

const MODULE_LABEL = { COMPANION: 'Business Builder', EXPORT: 'Export Readiness', STATEMENTS: 'Expense Analysis', DISPUTE: 'Dispute evidence' };

function UploadForm({ types, onDone }) {
  const toast = useToast();
  const { t } = useLang();
  const file = useRef(null);
  const [form, setForm] = useState({ title: '', docType: 'OTHER', expiresOn: '' });
  const [picked, setPicked] = useState(null);
  const [busy, setBusy] = useState(false);
  const submit = async (e) => {
    e.preventDefault();
    if (!picked) { toast.error(t('Choose a file first.')); return; }
    setBusy(true);
    try {
      await vaultApi.upload(picked, form.title || picked.name, form.docType, form.expiresOn || null);
      toast.success(t('Saved to your vault (encrypted).'));
      setForm({ title: '', docType: 'OTHER', expiresOn: '' }); setPicked(null);
      if (file.current) file.current.value = '';
      onDone();
    } catch (err) { toast.error(err.message); }
    setBusy(false);
  };
  return (
    <form className="fm-panel form-grid" onSubmit={submit}>
      <label>{t('File (PDF, JPG or PNG, up to 8 MB)')}
        <input ref={file} type="file" accept=".pdf,image/jpeg,image/png" onChange={(e) => setPicked(e.target.files?.[0] || null)} />
      </label>
      <label>{t('Name')}<input value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} placeholder={t('e.g. Tax clearance certificate 2026')} /></label>
      <label>{t('Type')}
        <select value={form.docType} onChange={(e) => setForm({ ...form, docType: e.target.value })}>
          {types.map((ty) => <option key={ty} value={ty}>{titleCase(ty)}</option>)}
        </select>
      </label>
      <label>{t('Expires on (optional)')}<input type="date" value={form.expiresOn} onChange={(e) => setForm({ ...form, expiresOn: e.target.value })} /></label>
      <div className="form-actions"><button className="primary" disabled={busy}>{busy ? t('Encrypting and saving…') : t('Save document')}</button></div>
      <p className="muted span-all">{t('We remind you 30, 7 and 1 day before a document expires. Files are encrypted at rest and only you (and, for dispute evidence, the other party and an administrator) can open them.')}</p>
    </form>
  );
}

function DocRow({ d, types, onChanged }) {
  const toast = useToast();
  const { t } = useLang();
  const [edit, setEdit] = useState(false);
  const [form, setForm] = useState({ title: d.title, docType: d.docType, expiresOn: d.expiresOn || '' });
  const [tone, label] = EXPIRY[d.expiryStatus] || EXPIRY.NONE;
  const save = async () => {
    try { await vaultApi.update(d.id, { ...form, expiresOn: form.expiresOn || null }); setEdit(false); onChanged(); }
    catch (e) { toast.error(e.message); }
  };
  const remove = async () => {
    if (!window.confirm(t('Delete "{title}" permanently?', { title: d.title }))) return;
    try { await vaultApi.remove(d.id); toast.success(t('Document deleted.')); onChanged(); } catch (e) { toast.error(e.message); }
  };
  return (
    <article className="panel doc-card">
      <div className="row-wrap" style={{ justifyContent: 'space-between' }}>
        <span className="doc-type">{titleCase(d.docType)}</span>
        <Badge tone={tone}>{d.expiryStatus === 'EXPIRING' ? t(d.daysToExpiry === 1 ? 'Expires in {n} day' : 'Expires in {n} days', { n: d.daysToExpiry }) : t(label)}</Badge>
      </div>
      {edit ? (
        <div className="stack">
          <input value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} aria-label={t('Name')} />
          <select value={form.docType} onChange={(e) => setForm({ ...form, docType: e.target.value })} aria-label={t('Type')}>
            {types.map((ty) => <option key={ty} value={ty}>{titleCase(ty)}</option>)}
          </select>
          <input type="date" value={form.expiresOn} onChange={(e) => setForm({ ...form, expiresOn: e.target.value })} aria-label={t('Expires on (optional)')} />
          <div className="row-wrap"><button className="primary small" onClick={save}>{t('save')}</button><button className="link-btn" onClick={() => setEdit(false)}>{t('cancel')}</button></div>
        </div>
      ) : (
        <>
          <h3>{d.title}</h3>
          <p className="muted">{d.fileName} · {bytes(d.sizeBytes)} · {t('added {date}', { date: date(d.createdAt) })}
            {d.expiresOn && <><br />{t('Expiry date:')} {date(d.expiresOn)}</>}
            {d.linkedModule && <><br />{t('Used in:')} {MODULE_LABEL[d.linkedModule] ? t(MODULE_LABEL[d.linkedModule]) : titleCase(d.linkedModule)}</>}</p>
          <div className="row-wrap">
            <button className="primary small" onClick={() => vaultApi.open(d.id).catch((e) => toast.error(e.message))}>{t('Open')}</button>
            <button className="secondary small" onClick={() => vaultApi.download(d.id, d.fileName).catch((e) => toast.error(e.message))}>{t('download')}</button>
            <button className="link-btn" onClick={() => setEdit(true)}>{t('Edit')}</button>
            <button className="link-btn danger" onClick={remove}>{t('Delete')}</button>
          </div>
        </>
      )}
    </article>
  );
}

export default function Documents() {
  const toast = useToast();
  const { t } = useLang();
  const [docs, setDocs] = useState(null);
  const [types, setTypes] = useState(['OTHER']);
  const [filter, setFilter] = useState('ALL');
  const [q, setQ] = useState('');
  const load = () => vaultApi.list().then(setDocs).catch((e) => { toast.error(e.message); setDocs([]); });
  useEffect(() => { load(); vaultApi.types().then(setTypes).catch(() => {}); /* eslint-disable-next-line */ }, []);

  const shown = useMemo(() => (docs || []).filter((d) => (filter === 'ALL' || (filter === 'ATTENTION' ? ['EXPIRED', 'EXPIRING'].includes(d.expiryStatus) : d.docType === filter))
    && (!q || `${d.title} ${d.fileName}`.toLowerCase().includes(q.toLowerCase()))), [docs, filter, q]);
  const attention = (docs || []).filter((d) => ['EXPIRED', 'EXPIRING'].includes(d.expiryStatus)).length;
  const present = [...new Set((docs || []).map((d) => d.docType))];

  return (
    <>
      <div className="page-head">
        <div>
          <p className="eyebrow">{t('DOCUMENT VAULT')}</p>
          <h1>{t('Every certificate, in one safe place.')}</h1>
          <p className="muted">{t('Your ID, CIPC certificate, tax clearance, permits, GLOBALG.A.P. and PUC certificates and statements. Encrypted, with expiry reminders, and ready to attach to your export plan or loan application.')}</p>
        </div>
      </div>
      <div className="fm-body">
        <UploadForm types={types} onDone={load} />
        <div className="row-wrap" style={{ margin: '16px 0', justifyContent: 'space-between' }}>
          <div className="segmented">
            <button className={filter === 'ALL' ? 'on' : ''} onClick={() => setFilter('ALL')}>{t('All ({n})', { n: docs?.length || 0 })}</button>
            <button className={filter === 'ATTENTION' ? 'on' : ''} onClick={() => setFilter('ATTENTION')}>{t('Needs attention ({n})', { n: attention })}</button>
            {present.map((ty) => <button key={ty} className={filter === ty ? 'on' : ''} onClick={() => setFilter(ty)}>{titleCase(ty)}</button>)}
          </div>
          <input className="filter" placeholder={t('Search documents')} value={q} onChange={(e) => setQ(e.target.value)} aria-label={t('Search documents')} />
        </div>
        {!docs ? <SkeletonCards count={3} /> : !shown.length ? (
          <EmptyState icon="▣" title={docs.length ? 'No documents match' : 'Your vault is empty'}
                      text={docs.length ? 'Try another filter.' : 'Start with your ID and, if you have one, your CIPC registration certificate. Lenders and export agents ask for these first.'} />
        ) : (
          <div className="card-grid">{shown.map((d) => <DocRow key={d.id} d={d} types={types} onChanged={load} />)}</div>
        )}
      </div>
    </>
  );
}
