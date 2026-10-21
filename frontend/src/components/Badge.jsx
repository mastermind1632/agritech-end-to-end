import useLang from '../hooks/useLang';

/** Small coloured status pill. tone: good | warn | bad | info | neutral. Plain-text labels are translated. */
export default function Badge({ tone = 'neutral', children, title }) {
  const { t } = useLang();
  return <span className={`badge ${tone}`} title={title ? t(title) : undefined}>{typeof children === 'string' ? t(children) : children}</span>;
}

export function statusTone(status) {
  const s = String(status || '').toUpperCase();
  if (['DONE', 'COMPLETED', 'CONFIRMED', 'PASSED', 'VERIFIED', 'SHIPPED', 'ACTIVE', 'VALID', 'RESOLVED'].includes(s)) return 'good';
  if (['IN_PROGRESS', 'REVIEW', 'PENDING', 'RESPONDED', 'EXPIRING', 'DRAFT', 'UPCOMING', 'DISPATCHED', 'DELIVERED', 'PROCESSING'].includes(s)) return 'warn';
  if (['FAILED', 'BLOCKED', 'MISSED', 'EXPIRED', 'DISPUTED', 'OPEN', 'SUSPENDED', 'CANCELLED', 'DECLINED', 'UNUSUAL', 'DUPLICATE'].includes(s)) return 'bad';
  return 'neutral';
}
