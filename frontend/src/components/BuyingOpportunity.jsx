import { useRef, useState } from 'react';
import { recommendationApi } from '../lib/api';

const rand = value => {
  const number = Number(value);
  return value == null || !Number.isFinite(number) ? 'Unknown' :
    new Intl.NumberFormat('en-ZA', { style: 'currency', currency: 'ZAR' }).format(number);
};

export default function BuyingOpportunity({ item, reviewOrder }) {
  const [opportunity, setOpportunity] = useState(item);
  const [quantity, setQuantity] = useState(1);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const sequence = useRef(0);
  const quote = opportunity.quote;
  const changed = Number(quantity) !== quote.quantity;
  const check = async event => {
    event.preventDefault();
    const current = ++sequence.current;
    setBusy(true); setError('');
    try {
      const result = await recommendationApi.quote(item.id, quantity);
      if (sequence.current === current) setOpportunity(result);
    } catch (e) { if (sequence.current === current) setError(e.message); }
    finally { if (sequence.current === current) setBusy(false); }
  };
  return <article className="buying-opportunity">
    <h3>{opportunity.productName}</h3>
    <p>{opportunity.reason}</p>
    <form className="buying-quantity" onSubmit={check}>
      <label>Quantity<input type="number" min="1" max={opportunity.remainingQuantity} step="1"
        value={quantity} disabled={busy} onChange={e => setQuantity(e.target.value)} required /></label>
      <button className="secondary" disabled={busy}>{busy ? 'Checking...' : 'Check listed prices'}</button>
    </form>
    <p className="muted">{opportunity.remainingQuantity} units remaining</p>
    {changed ? <p role="status">Check prices for the changed quantity.</p> : <dl className="buying-prices">
      <div><dt>Listed total ({quote.quantity} units)</dt><dd>{rand(quote.listedTotal)}</dd></div>
      <div><dt>With listed {quote.listedDiscountPercent}% group discount</dt><dd>{rand(quote.indicativeGroupTotal)}</dd></div>
      <div><dt>Indicative difference</dt><dd>{rand(quote.indicativeDifference)}</dd></div>
    </dl>}
    <p className="buying-caution">Same listed product only. Discount is not supplier-confirmed.
      Pack specifications, delivery costs, stock and final payment terms must be checked with the supplier.</p>
    {!!opportunity.equivalentPrices?.length && <div className="buying-alternatives">
      <h4>Matching recorded specifications</h4>
      <p className="muted">Catalogue specification codes are user-entered, not independently verified.</p>
      <ul>{opportunity.equivalentPrices.map(price => <li key={price.productId}>
        <span>{price.productName} ({price.packSize} {price.packUnit}){price.productId === opportunity.recommendedProductId ? ' - this listing' : ''}</span>
        <strong>{rand(price.pricePerUnit)} / {price.packUnit}</strong>
      </li>)}</ul>
    </div>}
    {!opportunity.equivalentPrices?.length && <p className="muted">No pack specifications available for comparisons across listings.</p>}
    <small>Prices checked: {new Date(quote.checkedAt).toLocaleString()}</small>
    {error && <p className="farm-error" role="alert">{error}</p>}
    <button className="text-button" onClick={() => reviewOrder(opportunity.suggestedGroupOrderId)}>Review this order</button>
  </article>;
}
