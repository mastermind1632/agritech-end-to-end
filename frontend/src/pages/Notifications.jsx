export default function Notifications({ notices }) {
  const { events = [], unread = 0, error, loading, seen = [], markAllRead } = notices || {};
  return (
    <>
      <div className="page-head">
        <div>
          <p className="eyebrow">ACTIVITY CENTER</p>
          <h1>Notifications</h1>
          <p className="muted">Group orders, your commitments and AI insights. Updates automatically.</p>
        </div>
        <button className="secondary" onClick={markAllRead} disabled={!unread}>Mark all as read</button>
      </div>
      {error && <div className="error">{error}</div>}
      <section className="panel" style={{ margin: '0 48px 48px' }}>
        {events.map((e) => (
          <div className="list-row" key={e.id}>
            <span className="round-icon">{e.mark}</span>
            <div style={{ flex: 1 }}>
              <b>{e.title}</b>
              <p>{e.text}</p>
              <p style={{ color: '#9aa59f' }}>{e.time ? new Date(e.time).toLocaleString('en-ZA') : ''}</p>
            </div>
            {!seen.includes(e.id) && <span className="tag">NEW</span>}
          </div>
        ))}
        {!events.length && !loading && (
          <div className="empty"><span>o</span><b>Nothing yet</b>
            <p>Create or join a group order, or generate AI insights, and the activity will show up here.</p></div>
        )}
      </section>
    </>
  );
}