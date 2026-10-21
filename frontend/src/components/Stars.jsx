import useLang from '../hooks/useLang';

export function Stars({ value, count = 0 }) {
  const { t } = useLang();
  if (!count) return <span className="stars"><span className="muted">{t('No ratings yet')}</span></span>;
  const full = Math.round(value || 0);
  return (
    <span className="stars" title={t('{value} out of 5', { value })}>
      <span aria-hidden="true">{'★'.repeat(full)}{'☆'.repeat(5 - full)}</span>
      <span>{Number(value).toFixed(1)}</span>
      <span className="muted">({count})</span>
    </span>
  );
}

export function StarInput({ value, onChange, label }) {
  const { t } = useLang();
  return (
    <span className="star-input" role="radiogroup" aria-label={t(label)}>
      {[1, 2, 3, 4, 5].map((n) => (
        <button key={n} type="button" role="radio" aria-checked={value === n} aria-label={n > 1 ? t('{n} stars', { n }) : t('1 star')}
          className={n <= value ? 'on' : ''} onClick={() => onChange(n)}>★</button>
      ))}
    </span>
  );
}
