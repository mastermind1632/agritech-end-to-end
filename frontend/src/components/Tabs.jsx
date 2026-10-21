import useLang from '../hooks/useLang';

/** Row of tab buttons used across the module pages. tabs: [[id, label, count?]]; labels are translated. */
export default function Tabs({ tabs, value, onChange }) {
  const { t } = useLang();
  return (
    <div className="tabs" role="tablist">
      {tabs.map(([id, label, count]) => (
        <button key={id} role="tab" aria-selected={value === id} className={value === id ? 'primary' : 'secondary'} onClick={() => onChange(id)}>
          {typeof label === 'string' ? t(label) : label}{count ? <em className="tab-count">{count}</em> : null}
        </button>
      ))}
    </div>
  );
}
