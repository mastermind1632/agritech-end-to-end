import { useEffect, useRef, useState } from 'react';
import { assistantApi, recommendationApi } from '../lib/api';
import { hasRecordedActivity, summarizeMatches, spendingAlert } from '../lib/farmerInsights';
import '../ai.css';
import BuyingOpportunity from '../components/BuyingOpportunity';
import useLang from '../hooks/useLang';

export default function AI({ farmer, setPage, reviewOrder }) {
  const { t } = useLang();
  const [items, setItems] = useState([]);
  const [messages, setMessages] = useState([]);
  const [question, setQuestion] = useState('');
  const [sending, setSending] = useState(false);
  const [refreshing, setRefreshing] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [opportunityError, setOpportunityError] = useState('');
  const [chatError, setChatError] = useState('');
  const [insights, setInsights] = useState(null);
  const [conversationId, setConversationId] = useState(null);
  const [approval, setApproval] = useState(null);
  const [starting, setStarting] = useState(true);
  const pending = useRef(false);
  const session = useRef(0);
  const chatSession = useRef(0);
  const load = async () => {
    const current = ++session.current;
    setLoading(true); setError(''); setOpportunityError(''); setInsights(null); setItems([]);
    try {
      const results = await Promise.allSettled([
        assistantApi.buyingGroup(), assistantApi.similar(farmer.id),
        assistantApi.alerts(farmer.id), recommendationApi.list(farmer.id)
      ]);
      if (current !== session.current) return;
      const [group, similar, alerts, recommendations] = results;
      if (recommendations.status === 'fulfilled') setItems(recommendations.value || []);
      else setOpportunityError(recommendations.reason.message);
      if ([group, similar, alerts].every(r => r.status === 'fulfilled')) {
        setInsights({ group: group.value, similar: similar.value, alerts: alerts.value });
      }
      const failures = [group, similar, alerts].filter(r => r.status === 'rejected');
      if (failures.length) setError(failures.map(r => r.reason.message).join(' '));
    } catch (e) { if (current === session.current) setError(e.message); }
    finally { if (current === session.current) setLoading(false); }
  };
  const openConversation = async (current = chatSession.current) => {
    setStarting(true); setChatError('');
    const key = `agritech_conversation_${farmer.id}`;
    try {
      let saved;
      const existing = localStorage.getItem(key);
      if (existing) {
        try { saved = await assistantApi.conversation(existing); }
        catch (e) {
          if (current !== chatSession.current) return;
          if (e.status !== 404) throw e;
          localStorage.removeItem(key);
        }
      }
      if (current !== chatSession.current) return;
      if (!saved) saved = await assistantApi.createConversation();
      if (current !== chatSession.current) return;
      setConversationId(saved.conversationId); setMessages(saved.messages || []); setApproval(saved.approval);
      localStorage.setItem(key, saved.conversationId);
    } catch (e) { if (current === chatSession.current) setChatError(e.message); }
    finally { if (current === chatSession.current) setStarting(false); }
  };
  useEffect(() => {
    const current = ++chatSession.current;
    pending.current = false; setSending(false); setRefreshing(false);
    setMessages([]); setQuestion(''); setInsights(null); setItems([]); setChatError('');
    setConversationId(null); setApproval(null);
    openConversation(current);
    load();
    return () => { session.current++; chatSession.current++; };
  }, [farmer.id]);
  const restoreConversation = async current => {
    try {
      const saved = await assistantApi.conversation(conversationId);
      if (current === chatSession.current) { setMessages(saved.messages || []); setApproval(saved.approval); }
      return true;
    } catch { return false; }
  };
  const send = async (event, suggestedPrompt) => {
    event.preventDefault();
    const prompt = (suggestedPrompt ?? question).trim();
    if (!prompt || pending.current || !conversationId || approval) return;
    const current = chatSession.current;
    pending.current = true; setSending(true); setChatError('');
    setMessages(previous => [...previous, { role: 'user', text: prompt }]); setQuestion('');
    try {
      const reply = await assistantApi.turn(conversationId, prompt);
      if (current === chatSession.current) {
        setMessages(previous => [...previous, { role: 'assistant', text: reply.response, sources: reply.sources, model: reply.model }]);
        setApproval(reply.approval || null);
      }
    } catch (e) { if (current === chatSession.current) {
      setChatError(e.message); setQuestion(prompt);
      setMessages(previous => previous.at(-1)?.role === 'user' && previous.at(-1)?.text === prompt ? previous.slice(0, -1) : previous);
      if (e.status === 409) await restoreConversation(current);
    } }
    finally { if (current === chatSession.current) { pending.current = false; setSending(false); } }
  };
  const clearConversation = async () => {
    if (pending.current) return;
    const id = farmer.id;
    const current = chatSession.current;
    pending.current = true; setStarting(true); setChatError('');
    try {
      if (conversationId) {
        try { await assistantApi.deleteConversation(conversationId); }
        catch (e) { if (e.status !== 404) throw e; }
      }
      if (current !== chatSession.current) return;
      localStorage.removeItem(`agritech_conversation_${id}`);
      setConversationId(null); setMessages([]); setQuestion(''); setApproval(null);
      const saved = await assistantApi.createConversation();
      if (current !== chatSession.current) return;
      setConversationId(saved.conversationId); localStorage.setItem(`agritech_conversation_${id}`, saved.conversationId);
    } catch (e) { if (current === chatSession.current) setChatError(e.message); }
    finally { if (current === chatSession.current) { pending.current = false; setStarting(false); } }
  };
  const requestReview = async action => {
    if (pending.current || !conversationId || approval) return;
    const current = chatSession.current;
    pending.current = true; setSending(true); setChatError('');
    try {
      const reply = await assistantApi.turn(conversationId, 'Review my group-order buying opportunity', action);
      if (current === chatSession.current) setApproval(reply.approval);
    } catch (e) { if (current === chatSession.current) {
      setChatError(e.message);
      if (e.status === 409) await restoreConversation(current);
    } }
    finally { if (current === chatSession.current) { pending.current = false; setSending(false); } }
  };
  const resolveApproval = async approved => {
    if (pending.current || !approval) return;
    const current = chatSession.current;
    pending.current = true; setSending(true); setChatError('');
    try {
      const reply = await assistantApi.resume(conversationId, approval.interruptId, approved);
      if (current !== chatSession.current) return;
      // Resume is already committed. A history refresh must not hide its result
      // or leave the consumed approval blocking the next question.
      setApproval(reply.approval || null);
      setMessages(previous => [...previous, { role: 'assistant', text: reply.response, sources: reply.sources }]);
      if (reply.actionReady) {
        reviewOrder(reply.actionReady.orderId, reply.actionReady.quantity);
        return;
      }
      const restored = await restoreConversation(current);
      if (current !== chatSession.current) return;
      if (!restored) setChatError('Your review was processed, but the conversation history could not be refreshed.');
    } catch (e) { if (current === chatSession.current) {
      setChatError(e.message);
      if (e.status === 409) await restoreConversation(current);
    } }
    finally { if (current === chatSession.current) { pending.current = false; setSending(false); } }
  };
  const generate = async () => {
    const id = farmer.id;
    const current = chatSession.current;
    setRefreshing(true); setOpportunityError('');
    try {
      const recommendations = await recommendationApi.generate(id);
      if (current === chatSession.current) setItems(recommendations || []);
    } catch (e) { if (current === chatSession.current) setOpportunityError(e.message); }
    finally { if (current === chatSession.current) setRefreshing(false); }
  };
  return <>
    <div className="page-head"><div><p className="eyebrow">{t('YOUR FARM')}</p><h1>{t('Farm advice and buying insights')}</h1></div></div>
    <section className="farm-assistant" aria-labelledby="assistant-title">
      <div className="farm-section-head"><h2 id="assistant-title">{t('Ask about your farm')}</h2>
        <button className="secondary" onClick={clearConversation} disabled={sending || starting}
          title={t('Delete this conversation and start a new one')}>{t('Clear conversation')}</button></div>
      <div className="farm-messages" aria-live="polite" aria-busy={sending}>
        {messages.length === 0 && !approval && !starting && <p className="muted">{t('What would you like to ask about seeds, farm costs or group buying?')}</p>}
        {messages.map((message, index) => <article className={`farm-message ${message.role}`} key={index}>
          <strong>{message.role === 'user' ? t('You') : t('Farm assistant')}</strong><p>{message.text}</p>
          {message.role === 'assistant' && message.model && <small>{message.model === 'AgriTech' ? t('Verified farm facts') : t('AI advice · {model}', { model: message.model })}</small>}
          {!!message.sources?.length && <details className="farm-source-details"><summary>{t('Sources ({n})', { n: message.sources.length })}</summary><ul className="farm-sources">{message.sources.map(source => <li key={source.id}>
            {source.url?.startsWith('https://') ? <a href={source.url} target="_blank" rel="noreferrer">{source.title}</a> : source.title}
            {source.region && <small>{source.region}</small>}
            {source.checkedOn && <small>{t('Source checked:')} {source.checkedOn}</small>}
            {source.reviewStatus && <small>{t(source.reviewStatus)}</small>}
          </li>)}</ul></details>}
        </article>)}
        {sending && <p role="status" className="thinking">{t('Thinking...')}</p>}
        {starting && <p role="status">{t('Loading your conversation...')}</p>}
        {approval && <section className="farm-approval" aria-labelledby="approval-title">
          <h3 id="approval-title">{t('Review required')}</h3>
          <p>{approval.productName}: {t('{n} units', { n: approval.quantity })}</p>
          <p>{t('Listed total: R {listed}. With the unconfirmed group discount: R {group}.', { listed: Number(approval.quote.listedTotal).toFixed(2), group: Number(approval.quote.indicativeGroupTotal).toFixed(2) })}</p>
          <p>{t(approval.notice)}</p>
          <div className="farm-approval-actions"><button className="primary" disabled={sending}
            onClick={() => resolveApproval(true)}>{t('Continue to order review')}</button>
            <button className="secondary" disabled={sending} onClick={() => resolveApproval(false)}>{t('Cancel review')}</button></div>
        </section>}
      </div>
      {chatError && <p className="farm-error" role="alert">{t(chatError)}</p>}
      {chatError && !conversationId && !starting && <button className="secondary" onClick={() => openConversation()}>{t('Retry conversation')}</button>}
      <form className="farm-question" onSubmit={send}>
        <label className="farm-input"><span>{t('Your question')}</span><input value={question} onChange={e => setQuestion(e.target.value)} maxLength={2000} disabled={sending || starting || !conversationId || !!approval} required /></label>
        <button className="primary" disabled={sending || starting || !conversationId || !!approval || !question.trim()}>{sending ? t('Waiting...') : t('Ask')}</button>
      </form>
    </section>
    <section className="farm-insights" aria-busy={loading}>
      <div className="farm-section-head"><h2>{t('Your farm comparisons')}</h2><button className="secondary" onClick={load} disabled={loading}>{loading ? t('loading') : t('Refresh comparisons')}</button></div>
      {error && <p className="farm-error" role="alert">{error}</p>}
      {!loading && insights && <>
        {hasRecordedActivity(insights.alerts.farmer) ? <>
          <dl className="farm-comparison-totals">
            <div><dt>{t('Similar overall spending patterns')}</dt><dd>{t(insights.group.otherFarmers === 1 ? '{n} other farm' : '{n} other farms', { n: insights.group.otherFarmers })}</dd></div>
            <div><dt>{t('Buying activity in common')}</dt><dd>{t(insights.similar.length === 1 ? '{n} match' : '{n} matches', { n: insights.similar.length })}</dd></div>
          </dl>
          {insights.similar.length ? <ul className="farm-match-summary">{summarizeMatches(insights.similar).map(summary => <li key={summary.label}>
            <span>{summary.label}</span><strong>{t(summary.count === 1 ? '{n} farm' : '{n} farms', { n: summary.count })}</strong>
          </li>)}</ul> : <p className="muted">{t('No farms with shared buying activity found yet.')}</p>}
          <button className="secondary" onClick={() => setPage('groups')}>{t('Browse open group orders')}</button>
        </> : <p className="muted">{t('No expense or group-order records available for a comparison.')}</p>}
        {hasRecordedActivity(insights.alerts.farmer) && <><h3>{t('Spending to review')}</h3>
        {insights.alerts.anomalies.length ? <ul className="farm-comparisons">{insights.alerts.anomalies.map((alert, index) => {
          const copy = spendingAlert(alert);
          return <li key={index}><strong>{copy.title}</strong><p>{copy.comparison}</p><p>{copy.action}</p></li>;
        })}</ul> : <p className="muted">{t('No unusual spending flagged in your current records.')}</p>}</>}
      </>}
    </section>
    <section className="farm-insights">
      <div className="farm-section-head"><h2>{t('Group-buying opportunities')}</h2><button className="primary" onClick={generate} disabled={refreshing}>{refreshing ? t('Checking...') : t('Find opportunities')}</button></div>
      <button className="secondary" disabled={sending || starting || !conversationId || !!approval}
        onClick={event => { send(event, 'Recommend group-buying opportunities for my farm and explain what to check before buying.'); document.getElementById('assistant-title')?.scrollIntoView({ behavior: 'smooth', block: 'start' }); }}>{t('Get AI buying advice')}</button>
      {opportunityError && <p className="farm-error" role="alert">{opportunityError}</p>}
      {items.length ? <div className="buying-opportunities">{items.map(item =>
        <BuyingOpportunity key={item.id} item={item} reviewOrder={reviewOrder} requestReview={requestReview}
          reviewDisabled={sending || starting || !conversationId || !!approval} />)}</div>
        : !opportunityError && <p className="muted">{loading ? t('Loading buying opportunities...') : t('No available group-buying opportunities right now.')} {!loading && <button className="text-button" onClick={() => setPage('groups')}>{t('Browse group orders')}</button>}</p>}
    </section>
  </>;
}
