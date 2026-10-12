import { useEffect, useState } from 'react';
import { farmMarketApi } from '../lib/api';
import {
  orderApi, reviewApi, imageUrl, uploadListingImage, deleteListingImage, shrinkImage,
  drivingEstimate, formatMinutes, geocode, getPosition
} from '../lib/market';
import { Stars, StarInput } from '../components/Stars';
import ReviewsPanel from '../components/Reviews';
import { ListingMap, MapPicker } from '../components/MapView';
import useCart, { lineTotal } from '../hooks/useCart';

const money = (n) => new Intl.NumberFormat('en-ZA', { style: 'currency', currency: 'ZAR' }).format(Number(n || 0));
const UNITS = ['kg', 'tonne', 'crate', 'bag', 'bunch', 'head', 'dozen', 'litre'];
const CATEGORIES = ['Vegetables', 'Fruit', 'Grain', 'Livestock', 'Dairy', 'Eggs & poultry', 'Other'];
const MAX_PHOTOS = 3;
const EMPTY = {
  product: '', category: 'Vegetables', grade: '', unit: 'kg', pricePerUnit: '', quantityAvailable: '',
  availableFrom: '', availableUntil: '', description: '', location: '', latitude: null, longitude: null,
  status: 'ACTIVE', costPerUnit: '', salePrice: '', saleEndsOn: ''
};
const STATUS_TEXT = {
  PENDING: 'Waiting for the farmer', CONFIRMED: 'Accepted', DISPATCHED: 'On the way',
  DELIVERED: 'Delivered, cash to confirm', COMPLETED: 'Completed', DECLINED: 'Declined', CANCELLED: 'Cancelled'
};

/* ---------- small pieces ---------- */

function PriceTag({ l }) {
  const pct = l.onSale ? Math.round((1 - Number(l.effectivePrice) / Number(l.pricePerUnit)) * 100) : 0;
  return (
    <span>
      <strong>
        {l.onSale && <span className="price-old">{money(l.pricePerUnit)}</span>}
        {money(l.effectivePrice)} / {l.unit}
      </strong>
      {l.onSale && <span className="sale-badge">SALE -{pct}%</span>}
    </span>
  );
}

function Photo({ ids, name }) {
  const [i, setI] = useState(0);
  if (!ids || !ids.length) return <div className="product-art">{name.slice(0, 1).toUpperCase()}</div>;
  const idx = i % ids.length;
  return (
    <div className="fm-photos" onClick={() => setI(idx + 1)} style={{ cursor: ids.length > 1 ? 'pointer' : 'default' }}>
      <img className="fm-card-img" loading="lazy" src={imageUrl(ids[idx])} alt={name} />
      {ids.length > 1 && <span className="count">{idx + 1} / {ids.length} (tap for next)</span>}
    </div>
  );
}

function ListingCard({ l, me, onReviews, children }) {
  const [route, setRoute] = useState(null);
  const [routing, setRouting] = useState(false);
  const canRoute = me && l.latitude != null && !l.mine;

  const estimate = async () => {
    setRouting(true);
    setRoute(await drivingEstimate(me, { lat: l.latitude, lng: l.longitude }));
    setRouting(false);
  };

  return (
    <article className="product-card">
      <Photo ids={l.imageIds} name={l.product} />
      <div className="product-body">
        <span className="tag">{l.status === 'ACTIVE' ? (l.category || 'Produce') : l.status.replace('_', ' ')}</span>
        <h3>{l.product}{l.grade ? ` (${l.grade})` : ''}</h3>
        <p style={{ marginBottom: 6 }}>
          {l.sellerName}{l.location || l.sellerLocation ? `, ${l.location || l.sellerLocation}` : ''}
        </p>
        <p style={{ marginBottom: 6 }}>
          Farmer: <Stars value={l.sellerRating} count={l.sellerRatingCount} />
          {' '}<button type="button" className="link-btn" onClick={() => onReviews(l)}>Reviews</button>
        </p>
        {l.productRatingCount > 0 && <p style={{ marginBottom: 6 }}>This product: <Stars value={l.productRating} count={l.productRatingCount} /></p>}
        <p className="muted" style={{ marginBottom: 6 }}>
          {l.quantityAvailable} {l.unit} available{l.availableUntil ? ` until ${l.availableUntil}` : ''}
          {l.saleEndsOn && l.onSale ? `. Sale ends ${l.saleEndsOn}` : ''}
        </p>
        {l.distanceKm != null && <p style={{ marginBottom: 6 }}>{l.distanceKm} km away in a straight line</p>}
        {canRoute && (
          <p style={{ marginBottom: 6 }}>
            {route
              ? <span>{route.rough ? 'Rough estimate: ' : 'By road: '}<b>{route.km.toFixed(0)} km, about {formatMinutes(route.minutes)}</b></span>
              : <button type="button" className="link-btn" onClick={estimate} disabled={routing}>{routing ? 'Working it out...' : 'Driving distance and time'}</button>}
          </p>
        )}
        {l.description && <p className="muted" style={{ marginBottom: 12 }}>{l.description}</p>}
        <div className="product-foot">
          <PriceTag l={l} />
          {children}
        </div>
      </div>
    </article>
  );
}

/* ---------- listing form (create / edit) ---------- */

function ListingForm({ initial, onSave, onCancel }) {
  const editing = Boolean(initial?.id);
  const [f, setF] = useState(() => ({
    ...EMPTY, ...initial,
    costPerUnit: initial?.costPerUnit ?? '',
    salePrice: initial?.onSale ? initial.salePrice : '',
    saleEndsOn: initial?.onSale ? (initial.saleEndsOn || '') : ''
  }));
  const [existing, setExisting] = useState(initial?.imageIds || []);
  const [newPhotos, setNewPhotos] = useState([]); // { blob, preview }
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [finding, setFinding] = useState(false);
  const set = (k) => (e) => setF({ ...f, [k]: e.target.value });
  const pos = f.latitude != null ? { lat: Number(f.latitude), lng: Number(f.longitude) } : null;
  const room = MAX_PHOTOS - existing.length - newPhotos.length;

  const addPhotos = async (e) => {
    const files = Array.from(e.target.files || []).slice(0, Math.max(room, 0));
    e.target.value = '';
    setError('');
    try {
      const done = [];
      for (const file of files) {
        const blob = await shrinkImage(file);
        done.push({ blob, preview: URL.createObjectURL(blob) });
      }
      setNewPhotos((p) => [...p, ...done]);
    } catch (err) { setError(err.message); }
  };

  const removeExisting = async (id) => {
    try { await deleteListingImage(initial.id, id); setExisting((x) => x.filter((v) => v !== id)); }
    catch (err) { setError(err.message); }
  };

  const findOnMap = async () => {
    if (!f.location) return setError('Type a town or area first, then press Find on map.');
    setError(''); setFinding(true);
    try {
      const p = await geocode(f.location);
      setF((x) => ({ ...x, latitude: p.lat, longitude: p.lng }));
    } catch (err) { setError(err.message); }
    setFinding(false);
  };

  const submit = async (e) => {
    e.preventDefault();
    setError('');
    setBusy(true);
    try {
      const num = (v) => (v === '' || v == null ? null : Number(v));
      await onSave({
        ...f,
        pricePerUnit: num(f.pricePerUnit),
        quantityAvailable: num(f.quantityAvailable),
        costPerUnit: num(f.costPerUnit),
        salePrice: num(f.salePrice),
        saleEndsOn: f.salePrice === '' ? null : (f.saleEndsOn || null),
        availableFrom: f.availableFrom || null,
        availableUntil: f.availableUntil || null
      }, newPhotos.map((p) => p.blob));
    } catch (err) {
      setError(err.message);
      setBusy(false);
    }
  };

  return (
    <form onSubmit={submit} className="fm-panel fm-form">
      <h3>{editing ? 'Edit listing' : 'New listing'}</h3>
      {error && <div className="error" style={{ marginBottom: 12 }}>{error}</div>}

      <p className="muted" style={{ marginBottom: 8 }}>Photos (up to {MAX_PHOTOS}). Good photos sell produce.</p>
      <div className="thumb-row">
        {existing.map((id) => (
          <div className="thumb" key={id}><img src={imageUrl(id)} alt="" /><button type="button" aria-label="Delete photo" onClick={() => removeExisting(id)}>×</button></div>
        ))}
        {newPhotos.map((p, i) => (
          <div className="thumb" key={p.preview}><img src={p.preview} alt="" /><button type="button" aria-label="Remove photo" onClick={() => setNewPhotos((x) => x.filter((_, j) => j !== i))}>×</button></div>
        ))}
        {room > 0 && (
          <label className="thumb" style={{ display: 'grid', placeItems: 'center', cursor: 'pointer', fontSize: 28, color: '#58866f' }}>
            +<input type="file" accept="image/*" multiple hidden onChange={addPhotos} />
          </label>
        )}
      </div>

      <div className="form-grid">
        <label>Product<input className="filter" required value={f.product} onChange={set('product')} placeholder="e.g. Butternut" /></label>
        <label>Category
          <select className="filter" value={f.category || ''} onChange={set('category')}>{CATEGORIES.map((c) => <option key={c}>{c}</option>)}</select>
        </label>
        <label>Grade<input className="filter" value={f.grade || ''} onChange={set('grade')} placeholder="e.g. Grade 1" /></label>
        <label>Unit
          <select className="filter" value={f.unit} onChange={set('unit')}>{UNITS.map((u) => <option key={u}>{u}</option>)}</select>
        </label>
        <label>Price per unit (ZAR)<input className="filter" type="number" inputMode="decimal" min="0.01" step="0.01" required value={f.pricePerUnit} onChange={set('pricePerUnit')} /></label>
        <label>Quantity available<input className="filter" type="number" inputMode="decimal" min="0" step="0.01" required value={f.quantityAvailable} onChange={set('quantityAvailable')} /></label>
        <label>Available from<input className="filter" type="date" value={f.availableFrom || ''} onChange={set('availableFrom')} /></label>
        <label>Available until<input className="filter" type="date" value={f.availableUntil || ''} onChange={set('availableUntil')} /></label>
      </div>

      <p className="muted" style={{ margin: '4px 0 8px' }}>Put it on sale (optional). Buyers pay the sale price until the end date.</p>
      <div className="form-grid">
        <label>Sale price (ZAR)<input className="filter" type="number" inputMode="decimal" min="0.01" step="0.01" value={f.salePrice} onChange={set('salePrice')} placeholder="Lower than normal price" /></label>
        <label>Sale ends<input className="filter" type="date" value={f.saleEndsOn || ''} onChange={set('saleEndsOn')} disabled={f.salePrice === ''} /></label>
        <label>Your cost per unit (private)<input className="filter" type="number" inputMode="decimal" min="0" step="0.01" value={f.costPerUnit} onChange={set('costPerUnit')} placeholder="Only you see this" /></label>
      </div>

      <label style={{ marginBottom: 14 }}>Description
        <textarea className="filter" rows={3} maxLength={1000} value={f.description || ''} onChange={set('description')} placeholder="Variety, how it was grown, packaging" />
      </label>

      <p className="muted" style={{ margin: '4px 0 8px' }}>Where can buyers collect it? Tap the map, search a town, or use your location.</p>
      <div className="row-wrap" style={{ marginBottom: 10 }}>
        <input className="filter" style={{ flex: '1 1 200px' }} value={f.location || ''} onChange={set('location')} placeholder="Town or area, e.g. Polokwane" />
        <button type="button" className="secondary" onClick={findOnMap} disabled={finding}>{finding ? 'Searching...' : 'Find on map'}</button>
        <button type="button" className="secondary" onClick={() => getPosition((latitude, longitude) => setF((x) => ({ ...x, latitude, longitude })), setError)}>Use my location</button>
      </div>
      <MapPicker value={pos} onChange={({ lat, lng }) => setF((x) => ({ ...x, latitude: lat, longitude: lng }))} />
      <p className="muted" style={{ marginBottom: 14 }}>
        {pos ? `Pin saved (${pos.lat.toFixed(3)}, ${pos.lng.toFixed(3)}). Tap the map to move it.` : 'No pin yet. Without one, buyers searching by distance will not find this listing.'}
      </p>

      <div className="row-wrap">
        <button className="primary" disabled={busy}>{busy ? 'Saving...' : 'Save listing'}</button>
        <button type="button" className="secondary" onClick={onCancel}>Cancel</button>
      </div>
    </form>
  );
}

/* ---------- ordering ---------- */

function OrderForm({ listing, onPlace, onCancel }) {
  const [qty, setQty] = useState('1');
  const [note, setNote] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const total = Number(qty || 0) * Number(listing.effectivePrice);

  const submit = async (e) => {
    e.preventDefault();
    setBusy(true); setError('');
    try { await onPlace({ listingId: listing.id, quantity: Number(qty), note }); }
    catch (err) { setError(err.message); setBusy(false); }
  };

  return (
    <form onSubmit={submit} className="fm-panel fm-form">
      <h3>Order {listing.product} from {listing.sellerName}</h3>
      <p className="muted" style={{ marginBottom: 12 }}>
        {money(listing.effectivePrice)} per {listing.unit}{listing.onSale ? ' (sale price)' : ''}. {listing.quantityAvailable} {listing.unit} available.
        You pay the farmer in cash when the produce is delivered.
      </p>
      {error && <div className="error" style={{ marginBottom: 12 }}>{error}</div>}
      <div className="form-grid">
        <label>Quantity ({listing.unit})
          <input className="filter" type="number" inputMode="decimal" min="0.01" step="0.01" max={listing.quantityAvailable} required value={qty} onChange={(e) => setQty(e.target.value)} />
        </label>
        <label>Delivery note
          <input className="filter" maxLength={500} value={note} onChange={(e) => setNote(e.target.value)} placeholder="Where and when to deliver" />
        </label>
      </div>
      <p style={{ marginBottom: 12 }}><strong>Total to pay on delivery: {money(total)}</strong></p>
      <div className="row-wrap">
        <button className="primary" disabled={busy}>{busy ? 'Sending...' : 'Place order'}</button>
        <button type="button" className="secondary" onClick={onCancel}>Cancel</button>
      </div>
    </form>
  );
}

function RateForm({ order, onDone }) {
  const [farmer, setFarmer] = useState(0);
  const [product, setProduct] = useState(0);
  const [comment, setComment] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const submit = async (e) => {
    e.preventDefault();
    if (!farmer) return setError('Tap the stars to rate the farmer first.');
    setBusy(true); setError('');
    try {
      await reviewApi.order(order.id, { farmerRating: farmer, productRating: product || null, comment });
      onDone();
    } catch (err) { setError(err.message); setBusy(false); }
  };

  return (
    <form onSubmit={submit} className="fm-form" style={{ marginTop: 12 }}>
      {error && <div className="error" style={{ marginBottom: 8 }}>{error}</div>}
      <p style={{ margin: '0 0 4px' }}>How was {order.sellerName}?</p>
      <StarInput value={farmer} onChange={setFarmer} label="Farmer rating" />
      <p style={{ margin: '8px 0 4px' }}>How was the {order.product}? (optional)</p>
      <StarInput value={product} onChange={setProduct} label="Product rating" />
      <label style={{ marginTop: 8 }}>Comment (optional)
        <textarea className="filter" rows={2} maxLength={500} value={comment} onChange={(e) => setComment(e.target.value)} />
      </label>
      <div className="row-wrap" style={{ marginTop: 10 }}>
        <button className="primary" disabled={busy}>{busy ? 'Sending...' : 'Send rating'}</button>
      </div>
    </form>
  );
}

function OrderCard({ o, onAction, onReload }) {
  const buyer = o.myRole === 'BUYER';
  const [rating, setRating] = useState(false);
  const label = (a) => ({
    confirm: 'Accept order', decline: 'Decline', cancel: 'Cancel order', dispatch: 'Mark as on the way',
    deliver: 'Enter delivery code', 'confirm-payment': buyer ? 'I paid the cash' : 'I received the cash'
  }[a] || a);
  const strong = (a) => ['confirm', 'dispatch', 'deliver', 'confirm-payment'].includes(a);

  return (
    <article className="product-card">
      {o.imageId && <img className="fm-card-img" loading="lazy" src={imageUrl(o.imageId)} alt={o.product} />}
      <div className="product-body">
        <span className="tag">{STATUS_TEXT[o.status] || o.status}</span>
        <h3>{o.quantity} {o.unit} of {o.product}</h3>
        <p style={{ marginBottom: 8 }}>{buyer ? `From ${o.sellerName}` : `For ${o.buyerName}`}. {money(o.pricePerUnit)} per {o.unit}. Total {money(o.totalAmount)}, cash on delivery.</p>
        {o.profit != null && <p className="profit-line" style={{ marginBottom: 8 }}>Your profit on this order: {money(o.profit)}</p>}
        {o.deliveryNote && <p className="muted" style={{ marginBottom: 8 }}>Note: {o.deliveryNote}</p>}
        {(o.sellerContact || o.buyerContact) && <p style={{ marginBottom: 8 }}>Contact: <strong>{o.sellerContact || o.buyerContact}</strong></p>}
        {o.deliveryCode && (
          <p className="order-code" style={{ marginBottom: 10 }}>
            Your delivery code: <strong>{o.deliveryCode}</strong><br />
            <span className="muted">Give this code to the farmer only when the produce reaches you.</span>
          </p>
        )}
        {o.status === 'DELIVERED' && (
          <p className="muted" style={{ marginBottom: 8 }}>
            Buyer confirmed cash: {o.buyerConfirmedPaid ? 'yes' : 'not yet'}. Farmer confirmed cash: {o.sellerConfirmedPaid ? 'yes' : 'not yet'}.
          </p>
        )}
        {buyer && o.reviewed && <p className="muted" style={{ marginBottom: 8 }}>You rated this order. Thank you.</p>}
        <details style={{ marginBottom: 10 }}>
          <summary>History</summary>
          {(o.events || []).map((e, i) => (
            <p key={i} className="muted" style={{ margin: '4px 0' }}>{new Date(e.at).toLocaleString('en-ZA')}: {e.note || e.toStatus} ({e.actorName})</p>
          ))}
        </details>
        <div className="row-wrap">
          {o.allowedActions.map((a) => (
            <button key={a} className={strong(a) ? 'primary' : 'secondary'} onClick={() => onAction(o, a)}>{label(a)}</button>
          ))}
          {o.canReview && !rating && <button className="primary" onClick={() => setRating(true)}>Rate this order</button>}
        </div>
        {o.canReview && rating && <RateForm order={o} onDone={() => { setRating(false); onReload(); }} />}
      </div>
    </article>
  );
}

/* ---------- cart ---------- */

function CartView({ cart, onBrowse, onCheckout, busy, failures }) {
  const { lines, itemCount, totalPrice } = cart;
  const [note, setNote] = useState('');

  if (!lines.length) {
    return (
      <div className="empty">
        <b>Your cart is empty</b>
        <p>Press "Add to cart" on any produce under Browse. You can add produce from several farmers and send all the orders in one go.</p>
        <button className="primary" onClick={onBrowse}>Browse produce</button>
      </div>
    );
  }

  const overStock = (l) => Number(l.quantity) > Number(l.maxQty);
  const invalid = lines.some((l) => !(Number(l.quantity) > 0) || overStock(l));

  return (
    <div className="cart-layout">
      <div className="cart-lines">
        {lines.map((l) => (
          <article className="cart-line fm-panel" key={l.id}>
            {l.imageId
              ? <img className="cart-thumb" src={imageUrl(l.imageId)} alt="" />
              : <div className="cart-thumb cart-thumb-letter">{l.product.slice(0, 1).toUpperCase()}</div>}
            <div className="cart-info">
              <h3>{l.product}{l.grade ? ` (${l.grade})` : ''}</h3>
              <p className="muted">From {l.sellerName}. {money(l.price)} per {l.unit}.</p>
              <div className="row-wrap" style={{ marginTop: 8 }}>
                <label className="qty-label">Quantity ({l.unit})
                  <input
                    className="filter" type="number" inputMode="decimal" min="0.01" step="0.01" max={l.maxQty}
                    value={l.quantity} onChange={(e) => cart.setQuantity(l.id, e.target.value)}
                  />
                </label>
                <button type="button" className="link-btn" onClick={() => cart.remove(l.id)}>Remove</button>
              </div>
              {overStock(l) && <p className="cart-warn">Only {l.maxQty} {l.unit} available. Please lower the quantity.</p>}
              {!(Number(l.quantity) > 0) && <p className="cart-warn">Enter a quantity above zero.</p>}
              {failures[l.id] && <p className="cart-warn">{failures[l.id]}</p>}
            </div>
            <strong className="cart-line-total">{money(lineTotal(l))}</strong>
          </article>
        ))}
      </div>

      <aside className="fm-panel cart-summary">
        <h3>Order summary</h3>
        <div className="cart-sum-row"><span>Items in cart</span><strong>{itemCount}</strong></div>
        <div className="cart-sum-row cart-sum-total"><span>Total price</span><strong>{money(totalPrice)}</strong></div>
        <p className="muted" style={{ margin: '10px 0' }}>
          You pay each farmer in cash on delivery. Each farmer gets their own order and must accept it, so they may reply at different times.
        </p>
        <label className="qty-label" style={{ marginBottom: 12 }}>Delivery note for all orders
          <input className="filter" maxLength={500} value={note} onChange={(e) => setNote(e.target.value)} placeholder="Where and when to deliver" />
        </label>
        <div className="row-wrap">
          <button className="primary" disabled={busy || invalid} onClick={() => onCheckout(note)}>
            {busy ? 'Sending orders...' : `Place ${itemCount} order${itemCount > 1 ? 's' : ''}`}
          </button>
          <button className="secondary" disabled={busy} onClick={() => { if (window.confirm('Remove everything from your cart?')) cart.clear(); }}>Clear cart</button>
        </div>
      </aside>
    </div>
  );
}

/* ---------- the page ---------- */

export default function FarmMarket({ farmer }) {
  const [tab, setTab] = useState('browse');
  const cart = useCart(farmer?.id);
  const [checkingOut, setCheckingOut] = useState(false);
  const [failures, setFailures] = useState({});
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');

  // Browse
  const [filters, setFilters] = useState({ q: '', category: '', maxPrice: '', availableOn: '', radiusKm: '50' });
  const [me, setMe] = useState(null);
  const [town, setTown] = useState('');
  const [needTown, setNeedTown] = useState(false);
  const [view, setView] = useState('list');
  const [results, setResults] = useState([]);
  const [loading, setLoading] = useState(false);
  const [orderTarget, setOrderTarget] = useState(null);
  const [picked, setPicked] = useState(null);
  const [reviewsFor, setReviewsFor] = useState(null);

  // My listings
  const [mine, setMine] = useState([]);
  const [editing, setEditing] = useState(null); // null = closed, {} = new, listing = edit

  // Orders
  const [buying, setBuying] = useState([]);
  const [selling, setSelling] = useState([]);
  const [summary, setSummary] = useState(null);

  const runSearch = async (coords = me) => {
    setError(''); setLoading(true);
    try {
      setResults(await farmMarketApi.search({
        q: filters.q, category: filters.category, maxPrice: filters.maxPrice, availableOn: filters.availableOn,
        ...(coords ? { lat: coords.lat, lng: coords.lng, radiusKm: filters.radiusKm } : {})
      }));
    } catch (e) { setError(e.message); }
    setLoading(false);
  };
  const loadMine = async () => { try { setMine(await farmMarketApi.mine()); } catch (e) { setError(e.message); } };
  const loadOrders = async () => {
    try {
      const [b, s, sum] = await Promise.all([orderApi.buying(), orderApi.selling(), orderApi.summary()]);
      setBuying(b); setSelling(s); setSummary(sum);
    } catch (e) { setError(e.message); }
  };
  const refreshAll = () => { runSearch(); loadMine(); loadOrders(); };

  useEffect(() => { refreshAll(); /* eslint-disable-next-line */ }, []);
  useEffect(() => { cart.sync(results); /* eslint-disable-next-line */ }, [results]);

  const useCoords = (coords) => { setMe(coords); setNeedTown(false); runSearch(coords); };
  const toggleDistance = () => {
    if (me) { setMe(null); runSearch(null); return; }
    getPosition((lat, lng) => useCoords({ lat, lng }), (msg) => { setError(msg); setNeedTown(true); });
  };
  const useTown = async () => {
    setError('');
    try { const p = await geocode(town); useCoords({ lat: p.lat, lng: p.lng }); }
    catch (e) { setError(e.message); }
  };

  const save = async (body, photos) => {
    const saved = editing?.id ? await farmMarketApi.update(editing.id, body) : await farmMarketApi.create(body);
    let photoError = '';
    for (const blob of photos) {
      try { await uploadListingImage(saved.id, blob); } catch (e) { photoError = e.message; }
    }
    setNotice(photoError ? `Listing saved, but a photo failed: ${photoError}` : (editing?.id ? 'Listing saved.' : 'Listing published.'));
    setEditing(null);
    loadMine(); runSearch();
  };
  const togglePause = async (l) => {
    try {
      await farmMarketApi.update(l.id, {
        ...l, status: l.status === 'PAUSED' ? 'ACTIVE' : 'PAUSED',
        salePrice: l.onSale ? l.salePrice : null, saleEndsOn: l.onSale ? l.saleEndsOn : null
      });
      loadMine(); runSearch();
    } catch (e) { setError(e.message); }
  };
  const remove = async (l) => {
    if (!window.confirm(`Remove "${l.product}" from the market?`)) return;
    try { await farmMarketApi.remove(l.id); setNotice('Listing removed.'); loadMine(); runSearch(); }
    catch (e) { setError(e.message); }
  };

  const placeOrder = async (body) => {
    await orderApi.place(body);
    setOrderTarget(null);
    setNotice('Order sent. The farmer needs to accept it before anything is reserved.');
    setTab('orders');
    refreshAll();
  };

  const orderAction = async (o, action) => {
    setError(''); setNotice('');
    let body = {};
    if (action === 'deliver') {
      const code = window.prompt('Enter the 6-digit delivery code the buyer gives you:');
      if (!code) return;
      body = { code };
    } else if (action === 'cancel' || action === 'decline') {
      const reason = window.prompt('Reason (optional). Press Cancel to keep the order.', '');
      if (reason === null) return;
      body = { reason };
    } else if (action === 'confirm-payment') {
      const who = o.myRole === 'BUYER' ? 'paid the full cash amount' : 'received the full cash amount';
      if (!window.confirm(`Confirm that you ${who} of ${money(o.totalAmount)}?`)) return;
    }
    try {
      await orderApi.act(o.id, action, body);
      setNotice(action === 'confirm-payment' ? 'Thanks, your confirmation is recorded.' : 'Order updated.');
      refreshAll();
    } catch (e) { setError(e.message); }
  };

  const addToCart = (l) => {
    setError('');
    const inCart = cart.lines.find((x) => x.id === l.id);
    cart.add(l, 1);
    setNotice(inCart ? `${l.product}: quantity increased in your cart.` : `${l.product} added to your cart.`);
  };

  // One order per cart line (each farmer has to accept their own order). Lines that go through
  // are removed from the cart; lines that fail stay, with the reason shown next to them.
  const checkout = async (note) => {
    setError(''); setNotice(''); setFailures({}); setCheckingOut(true);
    const sent = []; const failed = {};
    for (const l of cart.lines) {
      try {
        await orderApi.place({ listingId: l.id, quantity: Number(l.quantity), note });
        sent.push(l.id);
      } catch (e) { failed[l.id] = e.message; }
    }
    cart.removeMany(sent);
    setFailures(failed);
    setCheckingOut(false);
    const bad = Object.keys(failed).length;
    if (sent.length) {
      setNotice(bad
        ? `${sent.length} order${sent.length > 1 ? 's' : ''} sent. ${bad} could not be placed and ${bad > 1 ? 'are' : 'is'} still in your cart.`
        : `${sent.length} order${sent.length > 1 ? 's' : ''} sent. Each farmer needs to accept before anything is reserved.`);
      loadOrders(); runSearch();
      if (!bad) setTab('orders');
    } else if (bad) {
      setError('None of the orders could be placed. See the messages next to each item.');
    }
  };

  const startOrder = (l) => { setNotice(''); setOrderTarget(l); window.scrollTo({ top: 0, behavior: 'smooth' }); };
  const waiting = selling.filter((o) => o.status === 'PENDING').length;
  const withoutPin = results.filter((l) => l.latitude == null).length;

  const cardActions = (l) => (l.mine
    ? <span className="muted">Your listing</span>
    : (
      <span className="card-actions">
        <button className="secondary" onClick={() => addToCart(l)}>Add to cart</button>
        <button className="primary" onClick={() => startOrder(l)}>Order now</button>
      </span>
    ));

  return (
    <>
      <div className="page-head">
        <div>
          <p className="eyebrow">FARM MARKET</p>
          <h1>Buy and sell produce directly.</h1>
          <p className="muted">List what you grow, or find fresh produce from farmers near you.</p>
        </div>
        <div className="tabs">
          <button className={tab === 'browse' ? 'primary' : 'secondary'} onClick={() => setTab('browse')}>Browse</button>
          <button className={tab === 'cart' ? 'primary' : 'secondary'} onClick={() => setTab('cart')}>Cart ({cart.itemCount}){cart.itemCount ? ` · ${money(cart.totalPrice)}` : ''}</button>
          <button className={tab === 'mine' ? 'primary' : 'secondary'} onClick={() => setTab('mine')}>My listings ({mine.length})</button>
          <button className={tab === 'orders' ? 'primary' : 'secondary'} onClick={() => setTab('orders')}>Orders{waiting ? ` (${waiting} new)` : ''}</button>
        </div>
      </div>

      <div className="fm-body">
        {error && <div className="error" style={{ marginBottom: 14 }}>{error}</div>}
        {notice && <div className="notice" style={{ margin: '0 0 14px' }} role="status">{notice}</div>}
        {reviewsFor && <ReviewsPanel type="FARMER" id={reviewsFor.sellerId} title={`Reviews of ${reviewsFor.sellerName}`} onClose={() => setReviewsFor(null)} />}

        {tab === 'browse' && (
          <>
            {orderTarget && <OrderForm listing={orderTarget} onPlace={placeOrder} onCancel={() => setOrderTarget(null)} />}

            <div className="form-grid">
              <input className="filter" placeholder="Search produce" value={filters.q} onChange={(e) => setFilters({ ...filters, q: e.target.value })} />
              <select className="filter" value={filters.category} onChange={(e) => setFilters({ ...filters, category: e.target.value })}>
                <option value="">All categories</option>{CATEGORIES.map((c) => <option key={c}>{c}</option>)}
              </select>
              <input className="filter" type="number" inputMode="decimal" min="0" placeholder="Max price per unit" value={filters.maxPrice} onChange={(e) => setFilters({ ...filters, maxPrice: e.target.value })} />
              <input className="filter" type="date" title="Available on" value={filters.availableOn} onChange={(e) => setFilters({ ...filters, availableOn: e.target.value })} />
              <select className="filter" value={filters.radiusKm} onChange={(e) => setFilters({ ...filters, radiusKm: e.target.value })} disabled={!me}>
                {[10, 25, 50, 100, 250, 500].map((r) => <option key={r} value={r}>Within {r} km</option>)}
              </select>
              <button className="secondary" onClick={toggleDistance}>{me ? 'Stop filtering by distance' : 'Filter by distance from me'}</button>
            </div>
            {needTown && (
              <div className="row-wrap" style={{ marginBottom: 14 }}>
                <input className="filter" style={{ flex: '1 1 200px' }} placeholder="Your town, e.g. Polokwane" value={town} onChange={(e) => setTown(e.target.value)} />
                <button className="secondary" onClick={useTown}>Use this town</button>
              </div>
            )}
            <div className="row-wrap" style={{ marginBottom: 16, justifyContent: 'space-between' }}>
              <button className="primary" onClick={() => runSearch()} disabled={loading}>{loading ? 'Searching...' : 'Search'}</button>
              <div className="segmented">
                <button className={view === 'list' ? 'on' : ''} onClick={() => setView('list')}>List</button>
                <button className={view === 'map' ? 'on' : ''} onClick={() => setView('map')}>Map</button>
              </div>
            </div>

            {view === 'map' && (
              <>
                <ListingMap listings={results} me={me} selectedId={picked?.id} onSelect={setPicked} />
                {withoutPin > 0 && <p className="muted" style={{ marginBottom: 14 }}>{withoutPin} listing{withoutPin > 1 ? 's have' : ' has'} no map pin and only show in the list.</p>}
                {picked && (
                  <div className="product-grid" style={{ marginBottom: 20 }}>
                    <ListingCard l={picked} me={me} onReviews={setReviewsFor}>{cardActions(picked)}</ListingCard>
                  </div>
                )}
                {!picked && results.some((l) => l.latitude != null) && <p className="muted">Tap a dot on the map to see that listing.</p>}
              </>
            )}

            {view === 'list' && (
              <div className="product-grid">
                {results.map((l) => <ListingCard key={l.id} l={l} me={me} onReviews={setReviewsFor}>{cardActions(l)}</ListingCard>)}
              </div>
            )}
            {!results.length && !loading && !error && (
              <div className="empty"><b>No produce found</b><p>Try a wider search, remove the distance filter, or list your own produce under My listings.</p></div>
            )}
          </>
        )}

        {tab === 'cart' && (
          <CartView cart={cart} onBrowse={() => setTab('browse')} onCheckout={checkout} busy={checkingOut} failures={failures} />
        )}

        {tab === 'mine' && (
          <>
            {editing ? (
              <ListingForm initial={editing} onSave={save} onCancel={() => setEditing(null)} />
            ) : (
              <p style={{ marginBottom: 16 }}><button className="primary" onClick={() => { setNotice(''); setEditing({}); }}>New listing</button></p>
            )}
            <div className="product-grid">
              {mine.map((l) => (
                <ListingCard key={l.id} l={l} me={null} onReviews={setReviewsFor}>
                  <span className="card-actions">
                    <button className="secondary" onClick={() => { setNotice(''); setEditing(l); window.scrollTo({ top: 0, behavior: 'smooth' }); }}>Edit</button>
                    {l.status !== 'SOLD_OUT' && <button className="secondary" onClick={() => togglePause(l)}>{l.status === 'PAUSED' ? 'Resume' : 'Pause'}</button>}
                    <button className="secondary" onClick={() => remove(l)}>Remove</button>
                  </span>
                </ListingCard>
              ))}
            </div>
            {!mine.length && !editing && (
              <div className="empty"><b>You have no listings yet</b><p>Create a listing with photos, your price, quantity and pick-up spot so buyers can find you.</p></div>
            )}
          </>
        )}

        {tab === 'orders' && (
          <>
            {summary && (
              <>
                <h2 style={{ marginTop: 0 }}>Your sales</h2>
                <div className="summary-grid">
                  <div><span>Completed sales</span><strong>{summary.completedOrders}</strong></div>
                  <div><span>Waiting for you</span><strong>{summary.pendingOrders}</strong></div>
                  <div><span>Sales income</span><strong>{money(summary.revenue)}</strong></div>
                  <div><span>Profit{summary.ordersMissingCost ? ' (known costs)' : ''}</span><strong>{money(summary.profit)}</strong></div>
                </div>
                <p className="muted">
                  Income is added to My Finances automatically when you and the buyer both confirm the cash.
                  {summary.ordersMissingCost > 0 && ` ${summary.ordersMissingCost} sale${summary.ordersMissingCost > 1 ? 's have' : ' has'} no cost set. Add a cost per unit on your listings to see true profit.`}
                </p>
              </>
            )}
            <h2>Orders for your produce</h2>
            <div className="product-grid">
              {selling.map((o) => <OrderCard key={o.id} o={o} onAction={orderAction} onReload={refreshAll} />)}
            </div>
            {!selling.length && <div className="empty"><b>No orders yet</b><p>When a buyer orders from one of your listings it appears here for you to accept or decline.</p></div>}

            <h2>Orders you placed</h2>
            <div className="product-grid">
              {buying.map((o) => <OrderCard key={o.id} o={o} onAction={orderAction} onReload={refreshAll} />)}
            </div>
            {!buying.length && <div className="empty"><b>You have not ordered anything</b><p>Find produce under Browse and press Order. You pay in cash on delivery.</p></div>}
          </>
        )}
      </div>

      {tab === 'browse' && cart.itemCount > 0 && <div style={{ height: 76 }} />}
      {tab === 'browse' && cart.itemCount > 0 && (
        <div className="cart-bar" role="status">
          <span><b>{cart.itemCount}</b> item{cart.itemCount > 1 ? 's' : ''} in cart</span>
          <span className="cart-bar-total">{money(cart.totalPrice)}</span>
          <button className="primary" onClick={() => setTab('cart')}>View cart</button>
        </div>
      )}
    </>
  );
}
