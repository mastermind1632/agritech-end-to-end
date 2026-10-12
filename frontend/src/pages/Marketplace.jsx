import { useEffect, useState } from 'react';
import { marketApi } from '../lib/api';
import { reviewApi } from '../lib/market';
import { Stars, StarInput } from '../components/Stars';
import ReviewsPanel from '../components/Reviews';

const money = (n) => new Intl.NumberFormat('en-ZA', { style: 'currency', currency: 'ZAR' }).format(Number(n || 0));

function RateSupplier({ supplier, onDone, onCancel }) {
  const [rating, setRating] = useState(0);
  const [comment, setComment] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const submit = async (e) => {
    e.preventDefault();
    if (!rating) return setError('Tap the stars to choose a rating.');
    setBusy(true); setError('');
    try { await reviewApi.supplier(supplier.id, { rating, comment }); onDone(); }
    catch (err) { setError(err.message); setBusy(false); }
  };

  return (
    <form onSubmit={submit} className="fm-panel fm-form">
      <h3>Rate {supplier.name}</h3>
      <p className="muted" style={{ marginBottom: 10 }}>You can rate a supplier once you have joined one of their group orders. Rating again replaces your earlier rating.</p>
      {error && <div className="error" style={{ marginBottom: 10 }}>{error}</div>}
      <StarInput value={rating} onChange={setRating} label="Supplier rating" />
      <label style={{ marginTop: 10 }}>Comment (optional)
        <textarea className="filter" rows={2} maxLength={500} value={comment} onChange={(e) => setComment(e.target.value)} placeholder="Delivery, quality, price" />
      </label>
      <div className="row-wrap" style={{ marginTop: 12 }}>
        <button className="primary" disabled={busy}>{busy ? 'Sending...' : 'Send rating'}</button>
        <button type="button" className="secondary" onClick={onCancel}>Cancel</button>
      </div>
    </form>
  );
}

export default function Marketplace() {
  const [suppliers, setSuppliers] = useState([]);
  const [products, setProducts] = useState([]);
  const [ratings, setRatings] = useState({});
  const [selected, setSelected] = useState('all');
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [rating, setRating] = useState(null);
  const [reviewing, setReviewing] = useState(null);
  const [contactOf, setContactOf] = useState(null);

  const loadRatings = (list) =>
    reviewApi.ratings('SUPPLIER', list.map((s) => s.id)).then(setRatings).catch(() => setRatings({}));

  useEffect(() => {
    marketApi.suppliers().then((list) => { setSuppliers(list); loadRatings(list); }).catch((e) => setError(e.message));
  }, []);

  useEffect(() => {
    const chosen = selected === 'all' ? suppliers : suppliers.filter((s) => s.id === selected);
    Promise.all(chosen.map((s) => marketApi.products(s.id))).then((x) => setProducts(x.flat())).catch((e) => setError(e.message));
  }, [selected, suppliers]);

  return (
    <>
      <div className="page-head">
        <div>
          <p className="eyebrow">SUPPLIER SHOP</p>
          <h1>Source smarter.</h1>
          <p className="muted">Browse supplier listings and see how other farmers rate them before you commit your next farm purchase.</p>
        </div>
        <select className="filter" value={selected} onChange={(e) => setSelected(e.target.value)}>
          <option value="all">All suppliers</option>
          {suppliers.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
        </select>
      </div>

      <div className="fm-body">
        {error && <div className="error" style={{ marginBottom: 14 }}>{error}</div>}
        {notice && <div className="notice" style={{ margin: '0 0 14px' }} role="status">{notice}</div>}
        {rating && <RateSupplier supplier={rating} onCancel={() => setRating(null)}
          onDone={() => { setRating(null); setNotice('Thanks, your rating was saved.'); loadRatings(suppliers); }} />}
        {reviewing && <ReviewsPanel type="SUPPLIER" id={reviewing.id} title={`Reviews of ${reviewing.name}`} onClose={() => setReviewing(null)} />}

        <div className="product-grid">
          {products.map((p) => {
            const s = suppliers.find((x) => x.id === p.supplierId);
            const r = s ? ratings[s.id] : null;
            return (
              <article className="product-card" key={p.id}>
                <div className="product-art">▧</div>
                <div className="product-body">
                  <span className="tag">INPUT</span>
                  <h3>{p.productName}</h3>
                  <p style={{ marginBottom: 6 }}>{s?.name || 'Supplier'}{s?.location ? `, ${s.location}` : ''}</p>
                  <p style={{ marginBottom: 12 }}>
                    <Stars value={r?.average} count={r?.count || 0} />{' '}
                    {s && <button type="button" className="link-btn" onClick={() => setReviewing(s)}>Reviews</button>}
                  </p>
                  {contactOf === p.id && <p style={{ marginBottom: 12 }}>Contact: <strong>{s?.contact || 'Not listed'}</strong></p>}
                  <div className="product-foot">
                    <strong>{money(p.price)}</strong>
                    <span className="row-wrap">
                      <button className="secondary" onClick={() => setContactOf(contactOf === p.id ? null : p.id)}>Contact</button>
                      {s && <button className="secondary" onClick={() => { setNotice(''); setRating(s); window.scrollTo({ top: 0, behavior: 'smooth' }); }}>Rate</button>}
                    </span>
                  </div>
                </div>
              </article>
            );
          })}
        </div>
        {!products.length && !error && (
          <div className="empty"><span>▧</span><b>No supplier products yet</b><p>No supplier has listed products yet. Suppliers can add theirs in the Supplier Portal.</p></div>
        )}
      </div>
    </>
  );
}
