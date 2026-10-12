import { useEffect, useState } from 'react';
import { reviewApi } from '../lib/market';
import { Stars } from './Stars';

/** Shows the average and the latest written reviews for a FARMER, PRODUCT or SUPPLIER. */
export default function ReviewsPanel({ type, id, title, onClose }) {
  const [data, setData] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    let alive = true;
    reviewApi.summary(type, id).then((d) => alive && setData(d)).catch((e) => alive && setError(e.message));
    return () => { alive = false; };
  }, [type, id]);

  return (
    <div className="fm-panel">
      <div className="row-wrap" style={{ justifyContent: 'space-between' }}>
        <h3>{title}</h3>
        {onClose && <button type="button" className="secondary" onClick={onClose}>Close</button>}
      </div>
      {error && <div className="error">{error}</div>}
      {!data && !error && <p className="muted">Loading...</p>}
      {data && (
        <>
          <p><Stars value={data.average} count={data.count} /></p>
          {!data.reviews.length && <p className="muted">Nobody has written a review yet.</p>}
          {data.reviews.map((r, i) => (
            <div className="review" key={i}>
              <Stars value={r.rating} count={1} />
              <p>{r.comment || <span className="muted">No comment</span>}</p>
              <p className="muted">{r.reviewerName}, {new Date(r.at).toLocaleDateString('en-ZA')}</p>
            </div>
          ))}
        </>
      )}
    </div>
  );
}
