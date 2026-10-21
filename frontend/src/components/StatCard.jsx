import Icon from './Icon';
import useLang from '../hooks/useLang';

/** Headline number tile. label and sub are translated. */
export default function StatCard({ label, value, sub, icon, positive }) {
  const { t } = useLang();
  return (
    <div className="stat-card">
      <div className="stat-top"><span>{t(label)}</span><span className="stat-icon"><Icon name={icon} /></span></div>
      <strong>{value}</strong>
      <small className={positive ? 'positive' : ''}>{t(sub)}</small>
    </div>
  );
}
