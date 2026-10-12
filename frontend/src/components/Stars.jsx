export function Stars({ value, count = 0 }) {
  if (!count) return <span className="stars"><span className="muted">No ratings yet</span></span>;
  const full = Math.round(value || 0);
  return (
    <span className="stars" title={`${value} out of 5`}>
      <span aria-hidden="true">{'★'.repeat(full)}{'☆'.repeat(5 - full)}</span>
      <span>{Number(value).toFixed(1)}</span>
      <span className="muted">({count})</span>
    </span>
  );
}

export function StarInput({ value, onChange, label }) {
  return (
    <span className="star-input" role="radiogroup" aria-label={label}>
      {[1, 2, 3, 4, 5].map((n) => (
        <button key={n} type="button" role="radio" aria-checked={value === n} aria-label={`${n} star${n > 1 ? 's' : ''}`}
          className={n <= value ? 'on' : ''} onClick={() => onChange(n)}>★</button>
      ))}
    </span>
  );
}
