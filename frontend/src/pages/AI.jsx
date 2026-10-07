import { useEffect, useRef, useState } from 'react';
import { assistantApi, recommendationApi } from '../lib/api';
import { matchReason, spendingAlert } from '../lib/farmerInsights';
import '../ai.css';

export default function AI({ farmer, setPage }) {
  const [items, setItems] = useState([]);
  const [messages, setMessages] = useState([]);
  const [question, setQuestion] = useState('');
  const [sending, setSending] = useState(false);
  const [refreshing, setRefreshing] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [chatError, setChatError] = useState('');
  const [insights, setInsights] = useState(null);
  const pending = useRef(false);
  const session = useRef(0);
  const activeFarmer = useRef(farmer.id);
  const load = async () => {
    const current = ++session.current;
    setLoading(true); setError('');
    try {
      const [group, similar, alerts, recommendations] = await Promise.all([
        assistantApi.buyingGroup(), assistantApi.similar(farmer.id),
        assistantApi.alerts(farmer.id), recommendationApi.list(farmer.id)
      ]);
      if (current !== session.current) return;
      setInsights({ group, similar, alerts }); setItems(recommendations || []);
    } catch (e) { if (current === session.current) setError(e.message); }
    finally { if (current === session.current) setLoading(false); }
  };
  useEffect(() => {
    activeFarmer.current = farmer.id;
    setMessages([]); setInsights(null); setItems([]); setChatError('');
    load();
    return () => { session.current++; activeFarmer.current = null; };
  }, [farmer.id]);
  const send = async (event) => {
    event.preventDefault();
    const prompt = question.trim();
    if (!prompt || pending.current) return;
    const id = farmer.id;
    pending.current = true; setSending(true); setChatError('');
    setMessages(previous => [...previous, { role: 'user', text: prompt }]); setQuestion('');
    try {
      const reply = await assistantApi.chat(prompt);
      if (activeFarmer.current === id) setMessages(previous => [...previous, { role: 'assistant', text: reply.response, sources: reply.sources }]);
    } catch (e) { if (activeFarmer.current === id) { setChatError(e.message); setQuestion(prompt); } }
    finally { pending.current = false; setSending(false); }
  };
  const generate = async () => {
    const id = farmer.id;
    setRefreshing(true); setError('');
    try {
      const recommendations = await recommendationApi.generate(id);
      if (activeFarmer.current === id) setItems(recommendations || []);
    } catch (e) { if (activeFarmer.current === id) setError(e.message); }
    finally { setRefreshing(false); }
  };
  return <>
    <div className="page-head"><div><p className="eyebrow">YOUR FARM</p><h1>Farm advice and buying insights</h1></div></div>
    <section className="farm-assistant" aria-labelledby="assistant-title">
      <h2 id="assistant-title">Ask about your farm</h2>
      <div className="farm-messages" aria-live="polite" aria-busy={sending}>
        {messages.length === 0 && <p className="muted">What would you like to ask about seeds, farm costs or group buying?</p>}
        {messages.map((message, index) => <article className={`farm-message ${message.role}`} key={index}>
          <strong>{message.role === 'user' ? 'You' : 'Farm assistant'}</strong><p>{message.text}</p>
          {!!message.sources?.length && <small>Sources: {message.sources.map(source => source.title).join(', ')}</small>}
        </article>)}
        {sending && <p role="status">Thinking...</p>}
      </div>
      {chatError && <p className="farm-error" role="alert">{chatError}</p>}
      <form className="farm-question" onSubmit={send}>
        <label className="farm-input"><span>Your question</span><input value={question} onChange={e => setQuestion(e.target.value)} maxLength={2000} disabled={sending} required /></label>
        <button className="primary" disabled={sending || !question.trim()}>{sending ? 'Waiting...' : 'Ask'}</button>
      </form>
    </section>
    <section className="farm-insights" aria-busy={loading}>
      <div className="farm-section-head"><h2>Your farm comparisons</h2><button className="secondary" onClick={load} disabled={loading}>{loading ? 'Loading...' : 'Refresh comparisons'}</button></div>
      {error && <p className="farm-error" role="alert">{error}</p>}
      {!loading && insights && <>
        <h3>Farmers with similar buying activity</h3><p>{insights.group.otherFarmers} other farmers in your buying group.</p>
        {insights.similar.length ? <ul className="farm-comparisons">{insights.similar.map((match, index) => <li key={index}>
          <strong>Similar farm {index + 1}</strong><p>{match.reasons.map(matchReason).join('. ')}</p>
        </li>)}</ul> : <p className="muted">No other farms to compare yet.</p>}
        <h3>Spending to review</h3>
        {insights.alerts.anomalies.length ? <ul className="farm-comparisons">{insights.alerts.anomalies.map((alert, index) => {
          const copy = spendingAlert(alert);
          return <li key={index}><strong>{copy.title}</strong><p>{copy.comparison}</p><p>{copy.action}</p></li>;
        })}</ul> : <p className="muted">No unusual spending flagged in your current records.</p>}
      </>}
    </section>
    <section className="farm-insights">
      <div className="farm-section-head"><h2>Group-buying opportunities</h2><button className="primary" onClick={generate} disabled={refreshing}>{refreshing ? 'Checking...' : 'Find opportunities'}</button></div>
      {items.length ? <ul className="farm-comparisons">{items.map((item, index) => <li key={item.id || index}><p>{item.reason}</p><button className="text-button" onClick={() => setPage('groups')}>View group orders</button></li>)}</ul> : <p className="muted">No buying opportunities saved yet.</p>}
    </section>
  </>;
}
