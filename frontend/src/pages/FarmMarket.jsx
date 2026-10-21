import { useEffect, useState } from 'react';
import { disputeApi, farmMarketApi } from '../lib/api';
import { useToast } from '../components/Toast';
import { SkeletonCards } from '../components/Skeleton';
import {
  orderApi, reviewApi, imageUrl, uploadListingImage, deleteListingImage, shrinkImage,
  drivingEstimate, formatMinutes, geocode, getPosition
} from '../lib/market';
import { Stars, StarInput } from '../components/Stars';
import ReviewsPanel from '../components/Reviews';
import { ListingMap, MapPicker } from '../components/MapView';
import useCart, { lineTotal } from '../hooks/useCart';
import useLang from '../hooks/useLang';
import { tr } from '../lib/i18n';
import { dateTime } from '../lib/format';

const money = (n) => new Intl.NumberFormat('en-ZA', { style: 'currency', currency: 'ZAR' }).format(Number(n || 0));
const UNITS = ['kg', 'tonne', 'crate', 'bag', 'bunch', 'head', 'dozen', 'litre'];
const CATEGORIES = ['Vegetables', 'Fruit', 'Grain', 'Livestock', 'Dairy', 'Eggs & poultry', 'Other'];
const MAX_PHOTOS = 3;
const EMPTY = {
  product: '', category: 'Vegetables', grade: '', unit: 'kg', pricePerUnit: '', quantityAvailable: '',
  availableFrom: '', availableUntil: '', description: '', location: '', latitude: null, longitude: null,
  status: 'ACTIVE', costPerUnit: '', salePrice: '', saleEndsOn: ''
};
const PAGE_SIZE = 24;
const SORTS = [['', 'Newest first'], ['price_asc', 'Price: low to high'], ['price_desc', 'Price: high to low'], ['rating', 'Best rated farmers'], ['distance', 'Nearest']];
const STATUS_TEXT = {
  PENDING: 'Waiting for the farmer', CONFIRMED: 'Accepted', DISPATCHED: 'On the way',
  DELIVERED: 'Delivered, cash to confirm', COMPLETED: 'Completed', DECLINED: 'Declined', CANCELLED: 'Cancelled',
  DISPUTED: 'In dispute'
};

/* ---------- small pieces ---------- */

function PriceTag({ l }) {
  const { t } = useLang();
  const pct = l.onSale ? Math.round((1 - Number(l.effectivePrice) / Number(l.pricePerUnit)) * 100) : 0;
  return (
    <span>
      <strong>
        {l.onSale && <span className="price-old">{money(l.pricePerUnit)}</span>}
        {money(l.effectivePrice)} / {t(l.unit)}
      </strong>
      {l.onSale && <span className="sale-badge">{t('SALE -{pct}%', { pct })}</span>}
    </span>
  );
}

function Photo({ ids, name }) {
  const { t } = useLang();
  const [i, setI] = useState(0);
  if (!ids || !ids.length) return <div className="product-art">{name.slice(0, 1).toUpperCase()}</div>;
  const idx = i % ids.length;
  return (
    <div className="fm-photos" onClick={() => setI(idx + 1)} style={{ cursor: ids.length > 1 ? 'pointer' : 'default' }}>
      <img className="fm-card-img" loading="lazy" src={imageUrl(ids[idx])} alt={name} />
      {ids.length > 1 && <span className="count">{t('{n} / {total} (tap for next)', { n: idx + 1, total: ids.length })}</span>}
    </div>
  );
}

function ListingCard({ l, me, onReviews, children }) {
  const { t } = useLang();
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
        <span className="tag">{l.status === 'ACTIVE' ? t(l.category || 'Produce') : t(l.status === 'SOLD_OUT' ? 'Sold out' : l.status === 'PAUSED' ? 'Paused' : l.status.replace('_', ' '))}</span>
        <h3>{l.product}{l.grade ? ` (${l.grade})` : ''}</h3>
        <p style={{ marginBottom: 6 }}>
          {l.sellerName}{l.location || l.sellerLocation ? `, ${l.location || l.sellerLocation}` : ''}
        </p>
        {(l.sellerVerified || l.sellerExportReady) && (
          <p className="row-wrap" style={{ marginBottom: 6, gap: 6 }}>
            {l.sellerVerified && <span className="badge good" title={t('Business registration checked by AgriTech')}>✓ {t('Verified business')}</span>}
            {l.sellerExportReady && <span className="badge info" title={t('Has worked through an export-readiness plan')}>✈ {t('Export-ready')}</span>}
          </p>
        )}
        <p style={{ marginBottom: 6 }}>
          {t('Farmer:')} <Stars value={l.sellerRating} count={l.sellerRatingCount} />
          {' '}<button type="button" className="link-btn" onClick={() => onReviews(l)}>{t('Reviews')}</button>
        </p>
        {l.productRatingCount > 0 && <p style={{ marginBottom: 6 }}>{t('This product:')} <Stars value={l.productRating} count={l.productRatingCount} /></p>}
        <p className="muted" style={{ marginBottom: 6 }}>
          {t('{qty} {unit} available', { qty: l.quantityAvailable, unit: t(l.unit) })}{l.availableUntil ? ` ${t('until {date}', { date: l.availableUntil })}` : ''}
          {l.saleEndsOn && l.onSale ? `. ${t('Sale ends {date}', { date: l.saleEndsOn })}` : ''}
        </p>
        {l.distanceKm != null && <p style={{ marginBottom: 6 }}>{t('{km} km away in a straight line', { km: l.distanceKm })}</p>}
        {canRoute && (
          <p style={{ marginBottom: 6 }}>
            {route
              ? <span>{route.rough ? t('Rough estimate:') : t('By road:')} <b>{t('{km} km, about {time}', { km: route.km.toFixed(0), time: formatMinutes(route.minutes) })}</b></span>
              : <button type="button" className="link-btn" onClick={estimate} disabled={routing}>{routing ? t('Working it out...') : t('Driving distance and time')}</button>}
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
  const { t } = useLang();
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
    if (!f.location) return setError(t('Type a town or area first, then press Find on map.'));
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
      <h3>{editing ? t('Edit listing') : t('New listing')}</h3>
      {error && <div className="error" style={{ marginBottom: 12 }}>{error}</div>}

      <p className="muted" style={{ marginBottom: 8 }}>{t('Photos (up to {n}). Good photos sell produce.', { n: MAX_PHOTOS })}</p>
      <div className="thumb-row">
        {existing.map((id) => (
          <div className="thumb" key={id}><img src={imageUrl(id)} alt="" /><button type="button" aria-label={t('Delete photo')} onClick={() => removeExisting(id)}>×</button></div>
        ))}
        {newPhotos.map((p, i) => (
          <div className="thumb" key={p.preview}><img src={p.preview} alt="" /><button type="button" aria-label={t('Remove photo')} onClick={() => setNewPhotos((x) => x.filter((_, j) => j !== i))}>×</button></div>
        ))}
        {room > 0 && (
          <label className="thumb" style={{ display: 'grid', placeItems: 'center', cursor: 'pointer', fontSize: 28, color: 'var(--accent-soft-ink)' }}>
            +<input type="file" accept="image/*" multiple hidden onChange={addPhotos} />
          </label>
        )}
      </div>

      <div className="form-grid">
        <label>{t('Product')}<input className="filter" required value={f.product} onChange={set('product')} placeholder={t('e.g. Butternut')} /></label>
        <label>{t('Category')}
          <select className="filter" value={f.category || ''} onChange={set('category')}>{CATEGORIES.map((c) => <option key={c} value={c}>{t(c)}</option>)}</select>
        </label>
        <label>{t('Grade')}<input className="filter" value={f.grade || ''} onChange={set('grade')} placeholder={t('e.g. Grade 1')} /></label>
        <label>{t('Unit')}
          <select className="filter" value={f.unit} onChange={set('unit')}>{UNITS.map((u) => <option key={u} value={u}>{t(u)}</option>)}</select>
        </label>
        <label>{t('Price per unit (ZAR)')}<input className="filter" type="number" inputMode="decimal" min="0.01" step="0.01" required value={f.pricePerUnit} onChange={set('pricePerUnit')} /></label>
        <label>{t('Quantity available')}<input className="filter" type="number" inputMode="decimal" min="0" step="0.01" required value={f.quantityAvailable} onChange={set('quantityAvailable')} /></label>
        <label>{t('Available from')}<input className="filter" type="date" value={f.availableFrom || ''} onChange={set('availableFrom')} /></label>
        <label>{t('Available until')}<input className="filter" type="date" value={f.availableUntil || ''} onChange={set('availableUntil')} /></label>
      </div>

      <p className="muted" style={{ margin: '4px 0 8px' }}>{t('Put it on sale (optional). Buyers pay the sale price until the end date.')}</p>
      <div className="form-grid">
        <label>{t('Sale price (ZAR)')}<input className="filter" type="number" inputMode="decimal" min="0.01" step="0.01" value={f.salePrice} onChange={set('salePrice')} placeholder={t('Lower than normal price')} /></label>
        <label>{t('Sale ends')}<input className="filter" type="date" value={f.saleEndsOn || ''} onChange={set('saleEndsOn')} disabled={f.salePrice === ''} /></label>
        <label>{t('Your cost per unit (private)')}<input className="filter" type="number" inputMode="decimal" min="0" step="0.01" value={f.costPerUnit} onChange={set('costPerUnit')} placeholder={t('Only you see this')} /></label>
      </div>

      <label style={{ marginBottom: 14 }}>{t('Description')}
        <textarea className="filter" rows={3} maxLength={1000} value={f.description || ''} onChange={set('description')} placeholder={t('Variety, how it was grown, packaging')} />
      </label>

      <p className="muted" style={{ margin: '4px 0 8px' }}>{t('Where can buyers collect it? Tap the map, search a town, or use your location.')}</p>
      <div className="row-wrap" style={{ marginBottom: 10 }}>
        <input className="filter" style={{ flex: '1 1 200px' }} value={f.location || ''} onChange={set('location')} placeholder={t('Town or area, e.g. Polokwane')} />
        <button type="button" className="secondary" onClick={findOnMap} disabled={finding}>{finding ? t('Searching...') : t('Find on map')}</button>
        <button type="button" className="secondary" onClick={() => getPosition((latitude, longitude) => setF((x) => ({ ...x, latitude, longitude })), setError)}>{t('Use my location')}</button>
      </div>
      <MapPicker value={pos} onChange={({ lat, lng }) => setF((x) => ({ ...x, latitude: lat, longitude: lng }))} />
      <p className="muted" style={{ marginBottom: 14 }}>
        {pos ? t('Pin saved ({lat}, {lng}). Tap the map to move it.', { lat: pos.lat.toFixed(3), lng: pos.lng.toFixed(3) }) : t('No pin yet. Without one, buyers searching by distance will not find this listing.')}
      </p>

      <div className="row-wrap">
        <button className="primary" disabled={busy}>{busy ? t('Saving...') : t('Save listing')}</button>
        <button type="button" className="secondary" onClick={onCancel}>{t('cancel')}</button>
      </div>
    </form>
  );
}

/* ---------- ordering ---------- */

function OrderForm({ listing, onPlace, onCancel }) {
  const { t } = useLang();
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
      <h3>{t('Order {product} from {seller}', { product: listing.product, seller: listing.sellerName })}</h3>
      <p className="muted" style={{ marginBottom: 12 }}>
        {t('{price} per {unit}', { price: money(listing.effectivePrice), unit: t(listing.unit) })}{listing.onSale ? ` ${t('(sale price)')}` : ''}. {t('{qty} {unit} available', { qty: listing.quantityAvailable, unit: t(listing.unit) })}.
        {' '}{t('You pay the farmer in cash when the produce is delivered.')}
      </p>
      {error && <div className="error" style={{ marginBottom: 12 }}>{error}</div>}
      <div className="form-grid">
        <label>{t('Quantity ({unit})', { unit: t(listing.unit) })}
          <input className="filter" type="number" inputMode="decimal" min="0.01" step="0.01" max={listing.quantityAvailable} required value={qty} onChange={(e) => setQty(e.target.value)} />
        </label>
        <label>{t('Delivery note')}
          <input className="filter" maxLength={500} value={note} onChange={(e) => setNote(e.target.value)} placeholder={t('Where and when to deliver')} />
        </label>
      </div>
      <p style={{ marginBottom: 12 }}><strong>{t('Total to pay on delivery: {total}', { total: money(total) })}</strong></p>
      <div className="row-wrap">
        <button className="primary" disabled={busy}>{busy ? t('Sending...') : t('Place order')}</button>
        <button type="button" className="secondary" onClick={onCancel}>{t('cancel')}</button>
      </div>
    </form>
  );
}

function RateForm({ order, onDone }) {
  const { t } = useLang();
  const [farmer, setFarmer] = useState(0);
  const [product, setProduct] = useState(0);
  const [comment, setComment] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const submit = async (e) => {
    e.preventDefault();
    if (!farmer) return setError(t('Tap the stars to rate the farmer first.'));
    setBusy(true); setError('');
    try {
      await reviewApi.order(order.id, { farmerRating: farmer, productRating: product || null, comment });
      onDone();
    } catch (err) { setError(err.message); setBusy(false); }
  };

  return (
    <form onSubmit={submit} className="fm-form" style={{ marginTop: 12 }}>
      {error && <div className="error" style={{ marginBottom: 8 }}>{error}</div>}
      <p style={{ margin: '0 0 4px' }}>{t('How was {name}?', { name: order.sellerName })}</p>
      <StarInput value={farmer} onChange={setFarmer} label="Farmer rating" />
      <p style={{ margin: '8px 0 4px' }}>{t('How was the {product}? (optional)', { product: order.product })}</p>
      <StarInput value={product} onChange={setProduct} label="Product rating" />
      <label style={{ marginTop: 8 }}>{t('Comment (optional)')}
        <textarea className="filter" rows={2} maxLength={500} value={comment} onChange={(e) => setComment(e.target.value)} />
      </label>
      <div className="row-wrap" style={{ marginTop: 10 }}>
        <button className="primary" disabled={busy}>{busy ? t('Sending...') : t('Send rating')}</button>
      </div>
    </form>
  );
}

function DisputeForm({ order, onDone, onCancel }) {
  const { t } = useLang();
  const toast = useToast();
  const buyer = order.myRole === 'BUYER';
  const [reasons, setReasons] = useState({});
  const [form, setForm] = useState({ reason: '', description: '' });
  const [busy, setBusy] = useState(false);
  useEffect(() => { disputeApi.reasons().then(setReasons).catch(() => {}); }, []);
  const allowed = buyer ? ['QUALITY', 'SHORT_QUANTITY', 'NOT_DELIVERED', 'DAMAGED', 'OTHER'] : ['NO_SHOW', 'NON_PAYMENT', 'OTHER'];
  const submit = async (e) => {
    e.preventDefault(); setBusy(true);
    try { await disputeApi.raise(order.id, form); toast.success(t('Dispute raised. The other side has 48 hours to respond.')); onDone(); }
    catch (err) { toast.error(err.message); }
    setBusy(false);
  };
  return (
    <form className="stack dispute-form" onSubmit={submit}>
      <b>{t('Report a problem with this order')}</b>
      <select className="filter" value={form.reason} onChange={(e) => setForm({ ...form, reason: e.target.value })} required>
        <option value="">{t('What went wrong?')}</option>
        {allowed.map((r) => <option key={r} value={r}>{t(reasons[r] || r)}</option>)}
      </select>
      <textarea rows={3} value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} required minLength={10}
                placeholder={t('Describe what happened. You can add photos on the Disputes page afterwards.')} />
      <div className="row-wrap">
        <button className="primary" disabled={busy}>{t('Raise dispute')}</button>
        <button type="button" className="link-btn" onClick={onCancel}>{t('cancel')}</button>
      </div>
      <small className="muted">{t('The order is paused while the dispute is open. An administrator decides if you cannot agree.')}</small>
    </form>
  );
}

function OrderCard({ o, onAction, onReload, setPage }) {
  const { t } = useLang();
  const [disputing, setDisputing] = useState(false);
  const buyer = o.myRole === 'BUYER';
  const [rating, setRating] = useState(false);
  const label = (a) => t({
    confirm: 'Accept order', decline: 'Decline', cancel: 'Cancel order', dispatch: 'Mark as on the way',
    deliver: 'Enter delivery code', 'confirm-payment': buyer ? 'I paid the cash' : 'I received the cash', dispute: 'Report a problem'
  }[a] || a);
  const strong = (a) => ['confirm', 'dispatch', 'deliver', 'confirm-payment'].includes(a);

  return (
    <article className="product-card">
      {o.imageId && <img className="fm-card-img" loading="lazy" src={imageUrl(o.imageId)} alt={o.product} />}
      <div className="product-body">
        <span className="tag">{t(STATUS_TEXT[o.status] || o.status)}</span>
        <h3>{t('{qty} {unit} of {product}', { qty: o.quantity, unit: t(o.unit), product: o.product })}</h3>
        {!buyer && o.buyerReliability != null && (
          <p className="muted" style={{ marginBottom: 6 }} title={t("Share of this buyer's past orders that were completed without cancellation or a lost dispute")}>
            {t('Buyer reliability:')} <b className={o.buyerReliability >= 80 ? 'positive' : o.buyerReliability < 50 ? 'negative' : ''}>{o.buyerReliability}%</b></p>
        )}
        {(o.openDisputeId || o.latestDisputeId) && (
          <p style={{ marginBottom: 8 }}>
            <span className={`badge ${o.openDisputeId ? 'warn' : 'neutral'}`}>{o.openDisputeId ? t('Dispute open') : t('Had a dispute')}</span>{' '}
            <button className="link-btn" onClick={() => setPage?.('disputes')}>{t('View dispute')}</button>
          </p>
        )}
        <p style={{ marginBottom: 8 }}>{buyer ? t('From {name}', { name: o.sellerName }) : t('For {name}', { name: o.buyerName })}. {t('{price} per {unit}', { price: money(o.pricePerUnit), unit: t(o.unit) })}. {t('Total {total}, cash on delivery.', { total: money(o.totalAmount) })}</p>
        {o.profit != null && <p className="profit-line" style={{ marginBottom: 8 }}>{t('Your profit on this order:')} {money(o.profit)}</p>}
        {o.deliveryNote && <p className="muted" style={{ marginBottom: 8 }}>{t('Note:')} {o.deliveryNote}</p>}
        {(o.sellerContact || o.buyerContact) && <p style={{ marginBottom: 8 }}>{t('Contact:')} <strong>{o.sellerContact || o.buyerContact}</strong></p>}
        {o.deliveryCode && (
          <p className="order-code" style={{ marginBottom: 10 }}>
            {t('Your delivery code:')} <strong>{o.deliveryCode}</strong><br />
            <span className="muted">{t('Give this code to the farmer only when the produce reaches you.')}</span>
          </p>
        )}
        {o.status === 'DELIVERED' && (
          <p className="muted" style={{ marginBottom: 8 }}>
            {t('Buyer confirmed cash:')} {o.buyerConfirmedPaid ? t('yes') : t('not yet')}. {t('Farmer confirmed cash:')} {o.sellerConfirmedPaid ? t('yes') : t('not yet')}.
          </p>
        )}
        {buyer && o.reviewed && <p className="muted" style={{ marginBottom: 8 }}>{t('You rated this order. Thank you.')}</p>}
        <details style={{ marginBottom: 10 }}>
          <summary>{t('History')}</summary>
          {(o.events || []).map((e, i) => (
            <p key={i} className="muted" style={{ margin: '4px 0' }}>{dateTime(e.at)}: {e.note ? t(e.note) : t(STATUS_TEXT[e.toStatus] || e.toStatus)} ({e.actorName})</p>
          ))}
        </details>
        <div className="row-wrap">
          {o.allowedActions.filter((a) => a !== 'dispute').map((a) => (
            <button key={a} className={strong(a) ? 'primary' : 'secondary'} onClick={() => onAction(o, a)}>{label(a)}</button>
          ))}
          {o.allowedActions.includes('dispute') && !disputing && <button className="link-btn" onClick={() => setDisputing(true)}>{label('dispute')}</button>}
          {o.canReview && !rating && <button className="primary" onClick={() => setRating(true)}>{t('Rate this order')}</button>}
        </div>
        {o.canReview && rating && <RateForm order={o} onDone={() => { setRating(false); onReload(); }} />}
        {disputing && <DisputeForm order={o} onCancel={() => setDisputing(false)} onDone={() => { setDisputing(false); onReload(); }} />}
      </div>
    </article>
  );
}

/* ---------- cart ---------- */

function CartView({ cart, onBrowse, onCheckout, busy, failures }) {
  const { t } = useLang();
  const { lines, itemCount, totalPrice } = cart;
  const [note, setNote] = useState('');

  if (!lines.length) {
    return (
      <div className="empty">
        <span className="empty-icon">⛟</span>
        <b>{t('Your cart is empty')}</b>
        <p>{t('Press "Add to cart" on any produce under Browse. You can add produce from several farmers and send all the orders in one go.')}</p>
        <button className="primary" onClick={onBrowse}>{t('Browse produce')}</button>
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
              <p className="muted">{t('From {name}', { name: l.sellerName })}. {t('{price} per {unit}', { price: money(l.price), unit: t(l.unit) })}.</p>
              <div className="row-wrap" style={{ marginTop: 8 }}>
                <label className="qty-label">{t('Quantity ({unit})', { unit: t(l.unit) })}
                  <input
                    className="filter" type="number" inputMode="decimal" min="0.01" step="0.01" max={l.maxQty}
                    value={l.quantity} onChange={(e) => cart.setQuantity(l.id, e.target.value)}
                  />
                </label>
                <button type="button" className="link-btn" onClick={() => cart.remove(l.id)}>{t('Remove')}</button>
              </div>
              {overStock(l) && <p className="cart-warn">{t('Only {qty} {unit} available. Please lower the quantity.', { qty: l.maxQty, unit: t(l.unit) })}</p>}
              {!(Number(l.quantity) > 0) && <p className="cart-warn">{t('Enter a quantity above zero.')}</p>}
              {failures[l.id] && <p className="cart-warn">{failures[l.id]}</p>}
            </div>
            <strong className="cart-line-total">{money(lineTotal(l))}</strong>
          </article>
        ))}
      </div>

      <aside className="fm-panel cart-summary">
        <h3>{t('Order summary')}</h3>
        <div className="cart-sum-row"><span>{t('Items in cart')}</span><strong>{itemCount}</strong></div>
        <div className="cart-sum-row cart-sum-total"><span>{t('Total price')}</span><strong>{money(totalPrice)}</strong></div>
        <p className="muted" style={{ margin: '10px 0' }}>
          {t('You pay each farmer in cash on delivery. Each farmer gets their own order and must accept it, so they may reply at different times.')}
        </p>
        <label className="qty-label" style={{ marginBottom: 12 }}>{t('Delivery note for all orders')}
          <input className="filter" maxLength={500} value={note} onChange={(e) => setNote(e.target.value)} placeholder={t('Where and when to deliver')} />
        </label>
        <div className="row-wrap">
          <button className="primary" disabled={busy || invalid} onClick={() => onCheckout(note)}>
            {busy ? t('Sending orders...') : t(itemCount > 1 ? 'Place {n} orders' : 'Place {n} order', { n: itemCount })}
          </button>
          <button className="secondary" disabled={busy} onClick={() => { if (window.confirm(t('Remove everything from your cart?'))) cart.clear(); }}>{t('Clear cart')}</button>
        </div>
      </aside>
    </div>
  );
}

/* ---------- the page ---------- */

export default function FarmMarket({ farmer, setPage }) {
  const { t } = useLang();
  const canSell = !farmer?.role || farmer.role === 'FARMER';
  const [tab, setTab] = useState('browse');
  const cart = useCart(farmer?.id);
  const [checkingOut, setCheckingOut] = useState(false);
  const [failures, setFailures] = useState({});
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');

  // Browse
  const [filters, setFilters] = useState({ q: '', category: '', maxPrice: '', availableOn: '', radiusKm: '50', sort: '', minRating: '', verifiedOnly: false });
  const [shown, setShown] = useState(PAGE_SIZE);
  const [searched, setSearched] = useState(false);
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
        sort: filters.sort === 'distance' && !coords ? '' : filters.sort, minRating: filters.minRating,
        verifiedOnly: filters.verifiedOnly ? 'true' : '',
        ...(coords ? { lat: coords.lat, lng: coords.lng, radiusKm: filters.radiusKm } : {})
      }));
      setShown(PAGE_SIZE);
    } catch (e) { setError(e.message); }
    setLoading(false); setSearched(true);
  };
  const loadMine = async () => { if (!canSell) return; try { setMine(await farmMarketApi.mine()); } catch (e) { setError(e.message); } };
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
    setNotice(photoError ? t('Listing saved, but a photo failed: {error}', { error: photoError }) : (editing?.id ? t('Listing saved.') : t('Listing published.')));
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
    if (!window.confirm(t('Remove "{product}" from the market?', { product: l.product }))) return;
    try { await farmMarketApi.remove(l.id); setNotice(t('Listing removed.')); loadMine(); runSearch(); }
    catch (e) { setError(e.message); }
  };

  const placeOrder = async (body) => {
    await orderApi.place(body);
    setOrderTarget(null);
    setNotice(t('Order sent. The farmer needs to accept it before anything is reserved.'));
    setTab('orders');
    refreshAll();
  };

  const orderAction = async (o, action) => {
    setError(''); setNotice('');
    let body = {};
    if (action === 'deliver') {
      const code = window.prompt(t('Enter the 6-digit delivery code the buyer gives you:'));
      if (!code) return;
      body = { code };
    } else if (action === 'cancel' || action === 'decline') {
      const reason = window.prompt(t('Reason (optional). Press Cancel to keep the order.'), '');
      if (reason === null) return;
      body = { reason };
    } else if (action === 'confirm-payment') {
      const question = o.myRole === 'BUYER' ? 'Confirm that you paid the full cash amount of {total}?' : 'Confirm that you received the full cash amount of {total}?';
      if (!window.confirm(t(question, { total: money(o.totalAmount) }))) return;
    }
    try {
      await orderApi.act(o.id, action, body);
      setNotice(action === 'confirm-payment' ? t('Thanks, your confirmation is recorded.') : t('Order updated.'));
      refreshAll();
    } catch (e) { setError(e.message); }
  };

  const addToCart = (l) => {
    setError('');
    const inCart = cart.lines.find((x) => x.id === l.id);
    cart.add(l, 1);
    setNotice(inCart ? t('{product}: quantity increased in your cart.', { product: l.product }) : t('{product} added to your cart.', { product: l.product }));
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
        ? t('{sent} order(s) sent. {bad} could not be placed and are still in your cart.', { sent: sent.length, bad })
        : t('{sent} order(s) sent. Each farmer needs to accept before anything is reserved.', { sent: sent.length }));
      loadOrders(); runSearch();
      if (!bad) setTab('orders');
    } else if (bad) {
      setError(t('None of the orders could be placed. See the messages next to each item.'));
    }
  };

  const startOrder = (l) => { setNotice(''); setOrderTarget(l); window.scrollTo({ top: 0, behavior: 'smooth' }); };
  const waiting = selling.filter((o) => o.status === 'PENDING').length;
  const withoutPin = results.filter((l) => l.latitude == null).length;

  const cardActions = (l) => (l.mine
    ? <span className="muted">{t('Your listing')}</span>
    : (
      <span className="card-actions">
        <button className="secondary" onClick={() => addToCart(l)}>{t('Add to cart')}</button>
        <button className="primary" onClick={() => startOrder(l)}>{t('Order now')}</button>
      </span>
    ));

  return (
    <>
      <div className="page-head">
        <div>
          <p className="eyebrow">{t('FARM MARKET')}</p>
          <h1>{t('Buy and sell produce directly.')}</h1>
          <p className="muted">{t('List what you grow, or find fresh produce from farmers near you.')}</p>
        </div>
        <div className="tabs">
          <button className={tab === 'browse' ? 'primary' : 'secondary'} onClick={() => setTab('browse')}>{t('Browse')}</button>
          <button className={tab === 'cart' ? 'primary' : 'secondary'} onClick={() => setTab('cart')}>{t('Cart ({n})', { n: cart.itemCount })}{cart.itemCount ? ` · ${money(cart.totalPrice)}` : ''}</button>
          {canSell && <button className={tab === 'mine' ? 'primary' : 'secondary'} onClick={() => setTab('mine')}>{t('My listings ({n})', { n: mine.length })}</button>}
          <button className={tab === 'orders' ? 'primary' : 'secondary'} onClick={() => setTab('orders')}>{t('Orders')}{waiting ? ` (${t('{n} new', { n: waiting })})` : ''}</button>
        </div>
      </div>

      <div className="fm-body">
        {error && <div className="error" style={{ marginBottom: 14 }}>{error}</div>}
        {notice && <div className="notice" style={{ margin: '0 0 14px' }} role="status">{notice}</div>}
        {reviewsFor && <ReviewsPanel type="FARMER" id={reviewsFor.sellerId} title={t('Reviews of {name}', { name: reviewsFor.sellerName })} onClose={() => setReviewsFor(null)} />}

        {tab === 'browse' && (
          <>
            {orderTarget && <OrderForm listing={orderTarget} onPlace={placeOrder} onCancel={() => setOrderTarget(null)} />}

            <div className="form-grid">
              <input className="filter" placeholder={t('Search produce')} value={filters.q} onChange={(e) => setFilters({ ...filters, q: e.target.value })} />
              <select className="filter" value={filters.category} onChange={(e) => setFilters({ ...filters, category: e.target.value })}>
                <option value="">{t('All categories')}</option>{CATEGORIES.map((c) => <option key={c} value={c}>{t(c)}</option>)}
              </select>
              <input className="filter" type="number" inputMode="decimal" min="0" placeholder={t('Max price per unit')} value={filters.maxPrice} onChange={(e) => setFilters({ ...filters, maxPrice: e.target.value })} />
              <input className="filter" type="date" title={t('Available on')} value={filters.availableOn} onChange={(e) => setFilters({ ...filters, availableOn: e.target.value })} />
              <select className="filter" value={filters.radiusKm} onChange={(e) => setFilters({ ...filters, radiusKm: e.target.value })} disabled={!me}>
                {[10, 25, 50, 100, 250, 500].map((r) => <option key={r} value={r}>{t('Within {n} km', { n: r })}</option>)}
              </select>
              <select className="filter" value={filters.sort} onChange={(e) => setFilters({ ...filters, sort: e.target.value })} aria-label={t('Sort')}>
                {SORTS.filter(([k]) => k !== 'distance' || me).map(([k, l]) => <option key={k} value={k}>{t(l)}</option>)}
              </select>
              <select className="filter" value={filters.minRating} onChange={(e) => setFilters({ ...filters, minRating: e.target.value })} aria-label={t('Minimum farmer rating')}>
                <option value="">{t('Any rating')}</option><option value="3">{t('{n}★ and up', { n: 3 })}</option><option value="4">{t('{n}★ and up', { n: 4 })}</option><option value="4.5">{t('{n}★ and up', { n: 4.5 })}</option>
              </select>
              <label className="check inline"><input type="checkbox" checked={filters.verifiedOnly} onChange={(e) => setFilters({ ...filters, verifiedOnly: e.target.checked })} /><span>{t('Verified businesses only')}</span></label>
              <button className="secondary" onClick={toggleDistance}>{me ? t('Stop filtering by distance') : t('Filter by distance from me')}</button>
            </div>
            {needTown && (
              <div className="row-wrap" style={{ marginBottom: 14 }}>
                <input className="filter" style={{ flex: '1 1 200px' }} placeholder={t('Your town, e.g. Polokwane')} value={town} onChange={(e) => setTown(e.target.value)} />
                <button className="secondary" onClick={useTown}>{t('Use this town')}</button>
              </div>
            )}
            <div className="row-wrap" style={{ marginBottom: 16, justifyContent: 'space-between' }}>
              <button className="primary" onClick={() => runSearch()} disabled={loading}>{loading ? t('Searching...') : t('search')}</button>
              <div className="segmented">
                <button className={view === 'list' ? 'on' : ''} onClick={() => setView('list')}>{t('List')}</button>
                <button className={view === 'map' ? 'on' : ''} onClick={() => setView('map')}>{t('Map')}</button>
              </div>
            </div>

            {view === 'map' && (
              <>
                <ListingMap listings={results} me={me} selectedId={picked?.id} onSelect={setPicked} />
                {withoutPin > 0 && <p className="muted" style={{ marginBottom: 14 }}>{t('{n} listing(s) have no map pin and only show in the list.', { n: withoutPin })}</p>}
                {picked && (
                  <div className="product-grid" style={{ marginBottom: 20 }}>
                    <ListingCard l={picked} me={me} onReviews={setReviewsFor}>{cardActions(picked)}</ListingCard>
                  </div>
                )}
                {!picked && results.some((l) => l.latitude != null) && <p className="muted">{t('Tap a dot on the map to see that listing.')}</p>}
              </>
            )}

            {view === 'list' && loading && !searched && <SkeletonCards count={6} />}
            {view === 'list' && (
              <>
                {results.length > 0 && <p className="muted" style={{ marginBottom: 10 }}>{t('Showing {n} of {total} listings', { n: Math.min(shown, results.length), total: results.length })}</p>}
                <div className="product-grid">
                  {results.slice(0, shown).map((l) => <ListingCard key={l.id} l={l} me={me} onReviews={setReviewsFor}>{cardActions(l)}</ListingCard>)}
                </div>
                {results.length > shown && <div style={{ textAlign: 'center', margin: '18px 0' }}><button className="secondary" onClick={() => setShown(shown + PAGE_SIZE)}>{t('Show more')}</button></div>}
              </>
            )}
            {!results.length && !loading && !error && searched && (
              <div className="empty"><span className="empty-icon">⌕</span><b>{t('No produce found')}</b><p>{canSell ? t('Try a wider search, remove the distance filter, or list your own produce under My listings.') : t('Try a wider search or remove the distance filter.')}</p></div>
            )}
          </>
        )}

        {tab === 'cart' && (
          <CartView cart={cart} onBrowse={() => setTab('browse')} onCheckout={checkout} busy={checkingOut} failures={failures} />
        )}

        {tab === 'mine' && canSell && (
          <>
            {editing ? (
              <ListingForm initial={editing} onSave={save} onCancel={() => setEditing(null)} />
            ) : (
              <p style={{ marginBottom: 16 }}><button className="primary" onClick={() => { setNotice(''); setEditing({}); }}>{t('New listing')}</button></p>
            )}
            <div className="product-grid">
              {mine.map((l) => (
                <ListingCard key={l.id} l={l} me={null} onReviews={setReviewsFor}>
                  <span className="card-actions">
                    <button className="secondary" onClick={() => { setNotice(''); setEditing(l); window.scrollTo({ top: 0, behavior: 'smooth' }); }}>{t('Edit')}</button>
                    {l.status !== 'SOLD_OUT' && <button className="secondary" onClick={() => togglePause(l)}>{l.status === 'PAUSED' ? t('Resume') : t('Pause')}</button>}
                    <button className="secondary" onClick={() => remove(l)}>{t('Remove')}</button>
                  </span>
                </ListingCard>
              ))}
            </div>
            {!mine.length && !editing && (
              <div className="empty"><span className="empty-icon">❧</span><b>{t('You have no listings yet')}</b><p>{t('Create a listing with photos, your price, quantity and pick-up spot so buyers can find you.')}</p></div>
            )}
          </>
        )}

        {tab === 'orders' && (
          <>
            {canSell && summary && (
              <>
                <h2 style={{ marginTop: 0 }}>{t('Your sales')}</h2>
                <div className="summary-grid">
                  <div><span>{t('Completed sales')}</span><strong>{summary.completedOrders}</strong></div>
                  <div><span>{t('Waiting for you')}</span><strong>{summary.pendingOrders}</strong></div>
                  <div><span>{t('Sales income')}</span><strong>{money(summary.revenue)}</strong></div>
                  <div><span>{summary.ordersMissingCost ? t('Profit (known costs)') : t('Profit')}</span><strong>{money(summary.profit)}</strong></div>
                </div>
                <p className="muted">
                  {t('Income is added to My Finances automatically when you and the buyer both confirm the cash.')}
                  {summary.ordersMissingCost > 0 && ` ${t('{n} sale(s) have no cost set. Add a cost per unit on your listings to see true profit.', { n: summary.ordersMissingCost })}`}
                </p>
              </>
            )}
            {canSell && (
              <>
                <h2>{t('Orders for your produce')}</h2>
                <div className="product-grid">
                  {selling.map((o) => <OrderCard key={o.id} o={o} onAction={orderAction} onReload={refreshAll} setPage={setPage} />)}
                </div>
                {!selling.length && <div className="empty"><span className="empty-icon">⇄</span><b>{t('No orders yet')}</b><p>{t('When a buyer orders from one of your listings it appears here for you to accept or decline.')}</p></div>}
              </>
            )}

            <h2>{t('Orders you placed')}</h2>
            <div className="product-grid">
              {buying.map((o) => <OrderCard key={o.id} o={o} onAction={orderAction} onReload={refreshAll} setPage={setPage} />)}
            </div>
            {!buying.length && <div className="empty"><span className="empty-icon">⛟</span><b>{t('You have not ordered anything')}</b><p>{t('Find produce under Browse and press Order. You pay in cash on delivery.')}</p></div>}
          </>
        )}
      </div>

      {tab === 'browse' && cart.itemCount > 0 && <div style={{ height: 76 }} />}
      {tab === 'browse' && cart.itemCount > 0 && (
        <div className="cart-bar" role="status">
          <span><b>{cart.itemCount}</b> {t(cart.itemCount > 1 ? 'items in cart' : 'item in cart')}</span>
          <span className="cart-bar-total">{money(cart.totalPrice)}</span>
          <button className="primary" onClick={() => setTab('cart')}>{t('View cart')}</button>
        </div>
      )}
    </>
  );
}
