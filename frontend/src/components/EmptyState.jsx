import useLang from '../hooks/useLang';

/** Friendly empty screen with one clear next action ("No listings yet - create one"). Text props are translated. */
export default function EmptyState({ icon = '○', title, text, action, onAction, secondary, onSecondary }) {
  const { t } = useLang();
  return (
    <div className="empty">
      <span className="empty-icon">{icon}</span>
      <b>{t(title)}</b>
      {text && <p>{t(text)}</p>}
      {(action || secondary) && (
        <div className="row-wrap" style={{ justifyContent: 'center' }}>
          {action && <button className="primary" onClick={onAction}>{t(action)}</button>}
          {secondary && <button className="secondary" onClick={onSecondary}>{t(secondary)}</button>}
        </div>
      )}
    </div>
  );
}
