import { useEffect, useRef, useState } from 'react';
import { assistantApi } from '../lib/api';
import useLang from '../hooks/useLang';

const PROMPT = 'Recommend practical next steps for selling my harvest using my production profile and current buyer matches. Explain what I should do when there are no matching buyers.';

export default function MarketAdvice({ farmerId }) {
  const { t } = useLang();
  const [reply, setReply] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const mounted = useRef(false);
  const pending = useRef(false);
  useEffect(() => {
    mounted.current = true;
    return () => { mounted.current = false; };
  }, []);
  const generate = async () => {
    if (pending.current) return;
    pending.current = true;
    setBusy(true); setError('');
    const key = `agritech_market_advice_${farmerId}`;
    try {
      let conversationId = localStorage.getItem(key);
      if (!conversationId) {
        const created = await assistantApi.createConversation();
        conversationId = created.conversationId;
        localStorage.setItem(key, conversationId);
      }
      if (!mounted.current) return;
      let result;
      try { result = await assistantApi.turn(conversationId, PROMPT); }
      catch (e) {
        if (e.status !== 404 || !mounted.current) throw e;
        const created = await assistantApi.createConversation();
        localStorage.setItem(key, created.conversationId);
        result = await assistantApi.turn(created.conversationId, PROMPT);
      }
      if (mounted.current) setReply(result);
    } catch (e) { if (mounted.current) setError(e.message); }
    finally {
      pending.current = false;
      if (mounted.current) setBusy(false);
    }
  };
  const generated = reply?.model && reply.model !== 'AgriTech';
  return <section className="mi-advice" aria-labelledby="market-advice-title" aria-busy={busy}>
    <div className="mi-advice-head"><div>
      <p className="eyebrow">{t('YOUR FARM PLAN')}</p>
      <h2 id="market-advice-title">{t('What should I do next?')}</h2>
    </div><button className="primary" disabled={busy} onClick={generate}>
      {busy ? t('Preparing your advice...') : reply ? t('Refresh AI advice') : t('Get AI advice for my farm')}
    </button></div>
    <p className="muted">{t('Advice uses your production profile and the buyer requests currently recorded in AgriTech.')}</p>
    {busy && <p role="status">{t('Reviewing your farm and current opportunities. This may take a minute.')}</p>}
    {error && <p className="error" role="alert">{t(error)}</p>}
    {reply && <div aria-live="polite">
      <span className="tag">{generated ? t('AI advice · {model}', { model: reply.model }) : t('Verified farm facts')}</span>
      <p className="mi-advice-text">{reply.response}</p>
      {!!reply.sources?.length && <details><summary>{t('Sources ({n})', { n: reply.sources.length })}</summary>
        <ul>{reply.sources.map(source => <li key={source.id}>{source.title}</li>)}</ul>
      </details>}
    </div>}
  </section>;
}
