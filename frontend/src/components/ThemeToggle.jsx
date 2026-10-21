import useTheme from '../hooks/useTheme';
import useLang from '../hooks/useLang';

const SUN = <svg viewBox="0 0 24 24" aria-hidden="true" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round"><circle cx="12" cy="12" r="4.2" /><path d="M12 2.5v2.2M12 19.3v2.2M4.6 4.6l1.6 1.6M17.8 17.8l1.6 1.6M2.5 12h2.2M19.3 12h2.2M4.6 19.4l1.6-1.6M17.8 6.2l1.6-1.6" /></svg>;
const MOON = <svg viewBox="0 0 24 24" aria-hidden="true" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M20.5 14.2A8.5 8.5 0 1 1 9.8 3.5a6.8 6.8 0 0 0 10.7 10.7Z" /></svg>;

/** One-tap switch between light and dark. Settings has the full choice (including "same as my device"). */
export default function ThemeToggle({ className = '' }) {
  const { shown, setTheme } = useTheme();
  const { t } = useLang();
  const dark = shown === 'dark';
  const label = dark ? t('Switch to light theme') : t('Switch to dark theme');
  return (
    <button type="button" className={`theme-toggle ${dark ? 'is-dark' : ''} ${className}`} onClick={() => setTheme(dark ? 'light' : 'dark')}
            aria-label={label} title={label} aria-pressed={dark}>
      <span className="theme-toggle-track" aria-hidden="true">
        <span className="theme-toggle-thumb">{dark ? MOON : SUN}</span>
      </span>
    </button>
  );
}
