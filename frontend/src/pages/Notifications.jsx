export default function Notifications({ notices }) {
  const { events = [], unread = 0, error, loading, seen = [], markAllRead } = notices || {};
  return (
    <>
      <div className="page-head">
        <div>
          <p className="eyebrow">UPDATES</p>
          <h1>Notifications</h1>
          <p className="muted">Group orders you joined, new offers and buying tips. This page refreshes by itself.</p>
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
            <p>Join a group order or check your Buying Tips, and your updates will appear here.</p></div>
        )}
      </section>
    </>
  );
}