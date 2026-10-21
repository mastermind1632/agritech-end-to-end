import { useEffect, useState } from 'react';
import { adminApi, disputeApi, vaultApi } from '../lib/api';
import { SkeletonCards } from '../components/Skeleton';
import EmptyState from '../components/EmptyState';
import Badge from '../components/Badge';
import { useToast } from '../components/Toast';
import { onLive } from '../hooks/useLiveEvents';
import { bytes, dateTime, money, timeAgo } from '../lib/format';
import useLang from '../hooks/useLang';

const STATUS = {
  OPEN: ['warn', 'Waiting for response'],
  RESPONDED: ['info', 'Under review'],
  RESOLVED: ['good', 'Resolved'],
  WITHDRAWN: ['neutral', 'Withdrawn']
};
const RESOLUTION = {
  BUYER_FAVOUR: "Decided in the buyer's favour",
  SELLER_FAVOUR: "Decided in the farmer's favour",
  SPLIT: 'Settled between both parties'
};

/** One dispute with its timeline, evidence and the actions the viewer may take. Also used on the admin page. */
export function DisputeDetail({ dispute, onChanged }) {
  const toast = useToast();
  const { t } = useLang();
  const d = dispute;
  const [response, setResponse] = useState('');
  const [file, setFile] = useState(null);
  const [note, setNote] = useState('');
  const [decision, setDecision] = useState({ resolution: 'SPLIT', note: '' });
  const [busy, setBusy] = useState(false);
  const can = (a) => d.allowedActions?.includes(a);
  const run = async (fn, ok) => {
    setBusy(true);
    try { await fn(); if (ok) toast.success(t(ok)); onChanged?.(); } catch (e) { toast.error(e.message); }
    setBusy(false);
  };
  const [tone, label] = STATUS[d.status] || ['neutral', d.status];
  const overdue = d.status === 'OPEN' && d.respondBy && new Date(d.respondBy) < new Date();

  return (
    <article className="panel dispute">
      <div className="row-wrap" style={{ justifyContent: 'space-between' }}>
        <div>
          <p className="eyebrow">{t('ORDER #{id}', { id: d.orderId })} · {d.product ? t(d.product) : t('Produce')} {d.orderTotal != null && `· ${money(d.orderTotal)}`}</p>
          <h3>{t(d.reasonLabel)}</h3>
        </div>
        <div className="row-wrap"><Badge tone={tone}>{t(label)}</Badge>{overdue && <Badge tone="bad">Response overdue</Badge>}</div>
      </div>
      <ol className="timeline">
        <li className="done"><b>{d.myRole === 'RAISED_BY_ME' ? t('You raised the dispute') : t('{name} raised the dispute', { name: d.raisedByName })}</b> <span className="muted">{dateTime(d.createdAt)}</span>
          <p>{d.description}</p></li>
        {d.response ? (
          <li className="done"><b>{d.myRole === 'AGAINST_ME' ? t('You responded') : t('{name} responded', { name: d.againstName })}</b> <span className="muted">{dateTime(d.respondedAt)}</span><p>{d.response}</p></li>
        ) : d.status === 'OPEN' && (
          <li className="current"><b>{d.myRole === 'AGAINST_ME' ? t('Waiting for your response') : t('Waiting for the response from {name}', { name: d.againstName })}</b>
            <span className="muted"> · {t('due {when}', { when: dateTime(d.respondBy) })}</span>
            <p className="muted">{t('If there is no response by then, an administrator reviews the dispute anyway.')}</p></li>
        )}
        {d.status === 'RESOLVED' && (
          <li className="done"><b>{t(RESOLUTION[d.resolution] || d.resolution)}</b> <span className="muted">{dateTime(d.resolvedAt)}</span><p>{d.resolutionNote}</p></li>
        )}
        {d.status === 'WITHDRAWN' && <li className="done"><b>{t('Withdrawn by {name}', { name: d.raisedByName })}</b><p className="muted">{t('The order continued as normal.')}</p></li>}
        {(d.status === 'OPEN' || d.status === 'RESPONDED') && (
          <li><b>{t('Decision by an AgriTech administrator')}</b><p className="muted">{t('Both parties are notified. While the dispute is open, contact details stay visible so you can try to settle it directly.')}</p></li>
        )}
      </ol>

      <div className="evidence">
        <b>{t('Evidence ({n})', { n: d.evidence?.length || 0 })}</b>
        {d.evidence?.length ? (
          <ul className="link-list">
            {d.evidence.map((e) => (
              <li key={e.id}><button className="link-btn" onClick={() => vaultApi.open(e.id).catch((err) => toast.error(err.message))}>{e.title}</button>
                <span className="muted"> · {e.uploadedBy} · {bytes(e.sizeBytes)} · {timeAgo(e.createdAt)}</span></li>
            ))}
          </ul>
        ) : <p className="muted">{t('No photos or documents yet. Photos of the produce, delivery notes or proof of payment help a lot.')}</p>}
      </div>

      {can('respond') && (
        <div className="stack">
          <label>{t('Your side of the story')}
            <textarea rows={3} value={response} onChange={(e) => setResponse(e.target.value)} placeholder={t('What happened, from your side? Offer a solution if you can (e.g. a partial refund or a re-delivery).')} />
          </label>
          <button className="primary small" disabled={busy || response.trim().length < 5}
                  onClick={() => run(() => disputeApi.respond(d.id, response), 'Response sent.')}>{t('Send response')}</button>
        </div>
      )}
      {can('evidence') && (
        <div className="row-wrap evidence-form">
          <input type="file" accept=".pdf,image/jpeg,image/png" onChange={(e) => setFile(e.target.files?.[0] || null)} aria-label={t('Evidence file')} />
          <input className="filter" placeholder={t('Short note (optional)')} value={note} onChange={(e) => setNote(e.target.value)} />
          <button className="secondary small" disabled={busy || !file}
                  onClick={() => run(() => disputeApi.evidence(d.id, file, note).then(() => { setFile(null); setNote(''); }), 'Evidence added.')}>{t('Add evidence')}</button>
        </div>
      )}
      {can('resolve') && (
        <div className="resolve-box">
          <b>{t('Decision')}</b>
          <div className="choice-grid three">
            {Object.entries(RESOLUTION).map(([k, l]) => (
              <label key={k} className={decision.resolution === k ? 'on' : ''}>
                <input type="radio" name={`res-${d.id}`} checked={decision.resolution === k} onChange={() => setDecision({ ...decision, resolution: k })} />{t(l)}
              </label>
            ))}
          </div>
          <p className="muted">{t("Buyer's favour cancels the order (stock is returned if it was never delivered). Farmer's favour and a split return the order to where it was.")}</p>
          <textarea rows={2} value={decision.note} onChange={(e) => setDecision({ ...decision, note: e.target.value })} placeholder={t('Explain the decision to both parties')} />
          <button className="primary small" disabled={busy || decision.note.trim().length < 5}
                  onClick={() => run(() => adminApi.resolve(d.id, decision.resolution, decision.note), 'Decision sent to both parties.')}>{t('Resolve dispute')}</button>
        </div>
      )}
      {can('withdraw') && (
        <button className="link-btn" disabled={busy}
                onClick={() => window.confirm(t('Withdraw this dispute? The order will continue as normal.')) && run(() => disputeApi.withdraw(d.id), 'Dispute withdrawn.')}>
          {t('Withdraw dispute (we sorted it out)')}</button>
      )}
    </article>
  );
}

export default function Disputes({ setPage }) {
  const toast = useToast();
  const { t } = useLang();
  const [list, setList] = useState(null);
  const [showClosed, setShowClosed] = useState(false);
  const load = () => disputeApi.mine().then(setList).catch((e) => { toast.error(e.message); setList([]); });
  useEffect(() => { load(); return onLive('notification', (n) => { if (n?.category === 'DISPUTE') load(); }); /* eslint-disable-next-line */ }, []);
  const open = (list || []).filter((d) => d.status === 'OPEN' || d.status === 'RESPONDED');
  const closed = (list || []).filter((d) => !(d.status === 'OPEN' || d.status === 'RESPONDED'));

  return (
    <>
      <div className="page-head">
        <div>
          <p className="eyebrow">{t('FARM MARKET · DISPUTES')}</p>
          <h1>{t('Fair outcomes when an order goes wrong.')}</h1>
          <p className="muted">{t('Raise a dispute from the order in Farm Market. The other side has 48 hours to respond, both of you can add evidence, and an administrator makes the final decision.')}</p>
        </div>
        <button className="secondary" onClick={() => setPage('farmmarket')}>{t('Go to my orders')}</button>
      </div>
      <div className="fm-body">
        {!list ? <SkeletonCards count={2} /> : !list.length ? (
          <EmptyState icon="⚖" title="No disputes" text="Good news: none of your orders are in dispute. If something goes wrong, open the order in Farm Market and choose “Report a problem”." />
        ) : (
          <>
            {open.length ? <div className="stack">{open.map((d) => <DisputeDetail key={d.id} dispute={d} onChanged={load} />)}</div>
              : <EmptyState icon="✓" title="No open disputes" text="All your disputes are closed." />}
            {closed.length > 0 && (
              <>
                <button className="link-btn" style={{ margin: '16px 0' }} onClick={() => setShowClosed(!showClosed)}>
                  {t(showClosed ? 'Hide closed disputes ({n})' : 'Show closed disputes ({n})', { n: closed.length })}</button>
                {showClosed && <div className="stack">{closed.map((d) => <DisputeDetail key={d.id} dispute={d} onChanged={load} />)}</div>}
              </>
            )}
          </>
        )}
      </div>
    </>
  );
}
