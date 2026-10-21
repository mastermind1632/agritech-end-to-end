import { createContext, useCallback, useContext, useMemo, useRef, useState } from 'react';
import useLang from '../hooks/useLang';

const ToastContext = createContext({ show: () => {}, success: () => {}, error: () => {}, info: () => {} });

/** Small notices in the corner for success and failure, instead of blank screens or raw errors. */
export function ToastProvider({ children }) {
  const { t } = useLang();
  const [items, setItems] = useState([]);
  const seq = useRef(0);
  const dismiss = useCallback((id) => setItems((x) => x.filter((n) => n.id !== id)), []);
  const show = useCallback((kind, text, opts = {}) => {
    if (!text) return;
    const id = ++seq.current;
    setItems((x) => [...x.slice(-3), { id, kind, text, title: opts.title, action: opts.action }]);
    setTimeout(() => dismiss(id), opts.duration || (kind === 'error' ? 7000 : 4500));
  }, [dismiss]);
  const api = useMemo(() => ({
    show,
    success: (m, o) => show('success', m, o),
    error: (m, o) => show('error', m, o),
    info: (m, o) => show('info', m, o)
  }), [show]);
  return (
    <ToastContext.Provider value={api}>
      {children}
      <div className="toasts" role="status" aria-live="polite">
        {items.map((n) => (
          <div key={n.id} className={`toast ${n.kind}`}>
            <span className="toast-mark">{n.kind === 'success' ? '✓' : n.kind === 'error' ? '!' : 'i'}</span>
            <div className="toast-text">
              {n.title && <b>{t(n.title)}</b>}
              <span>{t(n.text)}</span>
              {n.action && <button className="link-btn" onClick={() => { n.action.onClick(); dismiss(n.id); }}>{t(n.action.label)}</button>}
            </div>
            <button className="toast-close" aria-label={t('Dismiss')} onClick={() => dismiss(n.id)}>×</button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

export const useToast = () => useContext(ToastContext);
