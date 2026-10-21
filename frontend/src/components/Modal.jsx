import useLang from '../hooks/useLang';

export default function Modal({ open, title, onClose, children }) {
  const { t } = useLang();
  if (!open) return null;
  return (
    <div className="modal-backdrop" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div className="modal" role="dialog" aria-modal="true">
        <div className="modal-head"><div><p className="eyebrow">{t('AGRI FINANCE')}</p><h2>{t(title)}</h2></div><button className="icon-btn" onClick={onClose} aria-label={t('Close')}>×</button></div>
        {children}
      </div>
    </div>
  );
}
