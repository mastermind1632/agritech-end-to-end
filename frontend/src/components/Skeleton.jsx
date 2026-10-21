import useLang from '../hooks/useLang';

/** Grey placeholder shapes while data loads, so the page never looks empty or broken. */
export function Skeleton({ width = '100%', height = 14, style }) {
  return <span className="skeleton" style={{ width, height, ...style }} aria-hidden="true" />;
}

export function SkeletonLines({ lines = 3 }) {
  const { t } = useLang();
  return (
    <div className="skeleton-lines" aria-busy="true" aria-label={t('Loading')}>
      {Array.from({ length: lines }).map((_, i) => <Skeleton key={i} width={`${90 - i * 12}%`} />)}
    </div>
  );
}

export function SkeletonCards({ count = 3 }) {
  const { t } = useLang();
  return (
    <div className="card-grid" aria-busy="true" aria-label={t('Loading')}>
      {Array.from({ length: count }).map((_, i) => (
        <div className="panel skeleton-card" key={i}>
          <Skeleton height={18} width="60%" />
          <Skeleton height={12} />
          <Skeleton height={12} width="80%" />
          <Skeleton height={34} width="40%" style={{ marginTop: 10 }} />
        </div>
      ))}
    </div>
  );
}
