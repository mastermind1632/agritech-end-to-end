import { useEffect, useMemo, useRef, useState } from 'react';
import useLive from '../hooks/useLive';
import Avatar from '../components/Avatar';
import { chatApi, groupApi, marketApi } from '../lib/api';

// DM rooms must match the backend: "dm:<idA>:<idB>" with the two ids sorted.
const dmRoom = (a, b) => (a < b ? `dm:${a}:${b}` : `dm:${b}:${a}`);
const timeOf = (t) => (t ? new Date(t).toLocaleTimeString('en-ZA', { hour: '2-digit', minute: '2-digit' }) : '');
const dayOf = (t) => (t ? new Date(t).toLocaleDateString('en-ZA', { day: 'numeric', month: 'short' }) : '');

export default function Chat({ farmer }) {
  const [active, setActive] = useState({ room: 'community', title: 'Community', sub: 'All farmers' });
  const [draft, setDraft] = useState('');
  const [sending, setSending] = useState(false);
  const [sendError, setSendError] = useState('');
  const [search, setSearch] = useState('');
  const bottomRef = useRef(null);

  const side = useLive(async () => {
    const [convos, farmers, orders, suppliers] = await Promise.all([
      chatApi.conversations(), chatApi.farmers(), groupApi.list('open'), marketApi.suppliers()
    ]);
    const products = (await Promise.all((suppliers || []).map((s) => marketApi.products(s.id)))).flat();
    return { convos: convos || [], farmers: farmers || [], orders: orders || [], products };
  }, [farmer.id], { interval: 10000 });

  const msgs = useLive(() => chatApi.history(active.room), [active.room], { interval: 3000 });
  const messages = (msgs.data || []).filter((m) => m.room === active.room);

  useEffect(() => { bottomRef.current?.scrollIntoView({ behavior: 'smooth' }); }, [messages.length, active.room]);

  const { convos = [], farmers = [], orders = [], products = [] } = side.data || {};
  const productName = (id) => products.find((p) => p.id === id)?.productName || 'Group order';

  const people = useMemo(() => {
    const q = search.trim().toLowerCase();
    return farmers.filter((f) => !q || (f.name || '').toLowerCase().includes(q) || (f.location || '').toLowerCase().includes(q));
  }, [farmers, search]);

  const openDm = (id, name, location) =>
    setActive({ room: dmRoom(farmer.id, id), title: name || 'Farmer', sub: location || 'Direct message' });

  const send = async (e) => {
    e.preventDefault();
    const body = draft.trim();
    if (!body || sending) return;
    setSending(true); setSendError('');
    try {
      await chatApi.send(active.room, body);
      setDraft('');
      await msgs.refresh();
      side.refresh();
    } catch (err) { setSendError(err.message); }
    finally { setSending(false); }
  };

  const onKey = (e) => { if (e.key === 'Enter' && !e.shiftKey) send(e); };

  const roomButton = (room, title, sub, badge) => (
    <button key={room} className={`chat-item ${active.room === room ? 'active' : ''}`}
      onClick={() => setActive({ room, title, sub })}>
      <span className="round-icon">{badge}</span>
      <span className="chat-item-text"><b>{title}</b><small>{sub}</small></span>
    </button>
  );

  return (
    <>
      <div className="page-head">
        <div>
          <p className="eyebrow">FARMER NETWORK</p>
          <h1>Chat</h1>
          <p className="muted">Talk to the community, coordinate group orders, or message a farmer directly.</p>
        </div>
      </div>

      <div className="chat-layout">
        <aside className="chat-side panel">
          <div className="chat-section">ROOMS</div>
          {roomButton('community', 'Community', 'All farmers', '♧')}

          {orders.length > 0 && <div className="chat-section">GROUP ORDERS</div>}
          {orders.map((o) => roomButton(`order:${o.id}`, productName(o.productId), `${o.currentQuantity}/${o.targetQuantity} units`, '▤'))}

          {convos.length > 0 && <div className="chat-section">DIRECT MESSAGES</div>}
          {convos.map((c) => (
            <button key={c.room} className={`chat-item ${active.room === c.room ? 'active' : ''}`}
              onClick={() => setActive({ room: c.room, title: c.otherName, sub: 'Direct message' })}>
              <Avatar id={c.otherId} name={c.otherName} size={32} />
              <span className="chat-item-text">
                <b>{c.otherName}</b>
                <small>{c.lastSenderId === farmer.id ? 'You: ' : ''}{c.lastBody}</small>
              </span>
            </button>
          ))}

          <div className="chat-section">START A CONVERSATION</div>
          <input className="chat-search" placeholder="Search farmers…" value={search} onChange={(e) => setSearch(e.target.value)} />
          <div className="chat-people">
            {people.map((f) => (
              <button key={f.id} className="chat-item" onClick={() => openDm(f.id, f.name, f.location)}>
                <Avatar id={f.id} name={f.name} size={32} />
                <span className="chat-item-text"><b>{f.name}</b><small>{f.location}</small></span>
              </button>
            ))}
            {!people.length && !side.loading && (
              <p className="muted" style={{ fontSize: 11, padding: '6px 10px' }}>No other farmers found.</p>
            )}
          </div>
          {side.error && <div className="error">{side.error}</div>}
        </aside>

        <section className="chat-main panel">
          <div className="chat-head"><b>{active.title}</b><span>{active.sub}</span></div>
          <div className="chat-messages">
            {messages.map((m, i) => {
              const mine = m.senderId === farmer.id;
              const newDay = i === 0 || dayOf(messages[i - 1].createdAt) !== dayOf(m.createdAt);
              return (
                <div key={m.id}>
                  {newDay && <div className="chat-day">{dayOf(m.createdAt)}</div>}
                  <div className={`chat-bubble-row ${mine ? 'mine' : ''}`}>
                    <div className="chat-bubble">
                      {!mine && <span className="chat-sender"><Avatar id={m.senderId} name={m.senderName} size={18} /> {m.senderName}</span>}
                      <p>{m.body}</p>
                      <small>{timeOf(m.createdAt)}</small>
                    </div>
                  </div>
                </div>
              );
            })}
            {!messages.length && !msgs.loading && !msgs.error && (
              <div className="empty"><span>✉</span><b>No messages yet</b><p>Say hello and start the conversation.</p></div>
            )}
            {msgs.error && <div className="error">{msgs.error}</div>}
            <div ref={bottomRef} />
          </div>
          {sendError && <div className="error" style={{ margin: '0 16px' }}>{sendError}</div>}
          <form className="chat-compose" onSubmit={send}>
            <textarea rows={1} maxLength={1000} placeholder="Type a message…" value={draft}
              onChange={(e) => setDraft(e.target.value)} onKeyDown={onKey} />
            <button className="primary" disabled={sending || !draft.trim()}>Send →</button>
          </form>
        </section>
      </div>
    </>
  );
}