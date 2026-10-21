import { useEffect, useState } from 'react';
import { reviewApi } from '../lib/market';
import { Stars } from './Stars';
import useLang from '../hooks/useLang';
import { date } from '../lib/format';

/** Shows the average and the latest written reviews for a FARMER, PRODUCT or SUPPLIER. */
export default function ReviewsPanel({ type, id, title, onClose }) {
  const { t } = useLang();
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
        <h3>{t(title)}</h3>
        {onClose && <button type="button" className="secondary" onClick={onClose}>{t('Close')}</button>}
      </div>
      {error && <div className="error">{error}</div>}
      {!data && !error && <p className="muted">{t('loading')}</p>}
      {data && (
        <>
          <p><Stars value={data.average} count={data.count} /></p>
          {!data.reviews.length && <p className="muted">{t('Nobody has written a review yet.')}</p>}
          {data.reviews.map((r, i) => (
            <div className="review" key={i}>
              <Stars value={r.rating} count={1} />
              <p>{r.comment || <span className="muted">{t('No comment')}</span>}</p>
              <p className="muted">{r.reviewerName}, {date(r.at)}</p>
            </div>
          ))}
        </>
      )}
    </div>
  );
}
