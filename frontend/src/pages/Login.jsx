import { useEffect, useState } from 'react';
import { authApi, farmerApi } from '../lib/api';
import useLang from '../hooks/useLang';
import { LANGUAGES } from '../lib/i18n';
import FarmScene from '../components/FarmScene';
import ThemeToggle from '../components/ThemeToggle';

const ROLES = [
  ['FARMER', 'Farmer', 'Sell produce, run your farm business, export'],
  ['BUYER', 'Buyer', 'Buy fresh produce directly from farmers'],
  ['SUPPLIER', 'Supplier', 'Sell inputs to farmer groups']
];

const SERVICES = ['Farm Market', 'Business Builder', 'Expense Analysis', 'Export Readiness', 'Market Insights'];

const strong = (p) => p.length >= 8 && /[A-Za-z]/.test(p) && /\d/.test(p);

export default function Login({ onLogin, resetToken, onResetDone }) {
  const { lang, setLang, t } = useLang();
  const [mode, setMode] = useState(resetToken ? 'reset' : 'login');
  const [form, setForm] = useState({ name: '', location: 'Polokwane', contact: '', email: '', password: '', role: 'FARMER', acceptTerms: false, otp: '' });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [info, setInfo] = useState('');
  const [devLink, setDevLink] = useState('');
  const [mfa, setMfa] = useState(false);
  const [service, setService] = useState(0);
  const change = (e) => setForm({ ...form, [e.target.name]: e.target.type === 'checkbox' ? e.target.checked : e.target.value });
  const switchTo = (m) => { setMode(m); setError(''); setInfo(''); setDevLink(''); setMfa(false); };

  // Highlight the five services one after another.
  useEffect(() => {
    const id = setInterval(() => setService((s) => (s + 1) % SERVICES.length), 3200);
    return () => clearInterval(id);
  }, []);

  const submit = async (e) => {
    e.preventDefault();
    setLoading(true); setError(''); setInfo('');
    try {
      if (mode === 'login') {
        const data = await farmerApi.login({ contact: form.contact, password: form.password, otp: mfa ? form.otp : undefined });
        if (data.mfaRequired) { setMfa(true); setInfo(t('Enter the 6-digit code from your authenticator app.')); return; }
        onLogin(data);
      } else if (mode === 'register') {
        if (!strong(form.password)) throw new Error(t('Use at least 8 characters with a letter and a number.'));
        if (!form.acceptTerms) throw new Error(t('Please accept the terms and privacy notice.'));
        const data = await farmerApi.register({ ...form, language: lang, email: form.email || null });
        onLogin({ ...data, farmer: { ...data.farmer, setupProfile: data.farmer?.role === 'FARMER' } });
      } else if (mode === 'forgot') {
        const res = await authApi.forgot(form.contact);
        setInfo(t(res.message));
        if (res.devResetLink) setDevLink(res.devResetLink);
      } else if (mode === 'reset') {
        if (!strong(form.password)) throw new Error(t('Use at least 8 characters with a letter and a number.'));
        const res = await authApi.reset(resetToken, form.password);
        setInfo(t(res.message));
        setForm({ ...form, password: '' });
        setMode('login');
        onResetDone?.();
      }
    } catch (err) {
      setError(t(err.message));
    } finally {
      setLoading(false);
    }
  };

  const titles = {
    login: [t('WELCOME BACK'), t('signIn'), t('Access your farm business platform.')],
    register: [t('GET STARTED'), t('createAccount'), t('Set up your workspace in under a minute.')],
    forgot: [t('ACCOUNT HELP'), t('forgot'), t('Enter your mobile number or email and we will send you a reset link.')],
    reset: [t('ACCOUNT HELP'), t('Choose a new password'), t('Use at least 8 characters with a letter and a number.')]
  }[mode];

  return (
    <div className="auth">
      <div className="auth-art">
        <FarmScene />
        <div className="auth-brand rise" style={{ '--i': 0 }}><div className="brand-mark"><img src="/icons/icon.svg" alt="" /></div><b>AgriTech</b></div>
        <div className="hero-copy">
          <p className="eyebrow rise" style={{ '--i': 1 }}>{t('ONE PLATFORM · FIVE SERVICES · BUILT FOR SOUTH AFRICAN FARMERS')}</p>
          <h1 className="rise" style={{ '--i': 2 }}>{t('Grow a farm into')} <i>{t('a business that lasts.')}</i></h1>
          <p className="rise" style={{ '--i': 3 }}>{t('Sell directly to buyers, formalise and plan your business, understand every rand you spend, and get export-ready, with guidance tagged to the authority that requires it.')}</p>
          <div className="hero-metrics">
            {SERVICES.map((s, i) => (
              <div key={s} className={`rise ${service === i ? 'lit' : ''}`} style={{ '--i': 4 + i }}><b>{String(i + 1).padStart(2, '0')}</b><span>{t(s)}</span></div>
            ))}
          </div>
        </div>
      </div>
      <div className="auth-panel">
        <div className="auth-card pop-in">
          <div className="auth-lang">
            <ThemeToggle />
            <select value={lang} onChange={(e) => setLang(e.target.value)} aria-label={t('language')}>
              {LANGUAGES.map(([code, name]) => <option key={code} value={code}>{name}</option>)}
            </select>
          </div>
          <div key={mode} className="auth-mode">
            <p className="eyebrow">{titles[0]}</p>
            <h2>{titles[1]}</h2>
            <p className="muted">{titles[2]}</p>
          </div>
          <form onSubmit={submit}>
            {mode === 'register' && (
              <>
                <fieldset className="role-pick">
                  <legend>{t('I am a')}</legend>
                  {ROLES.map(([value, label, hint]) => (
                    <label key={value} className={form.role === value ? 'on' : ''}>
                      <input type="radio" name="role" value={value} checked={form.role === value} onChange={change} />
                      <b>{t(label)}</b><span>{t(hint)}</span>
                    </label>
                  ))}
                </fieldset>
                <label>{t('Full name or business name')}<input name="name" value={form.name} onChange={change} placeholder={t('e.g. Thandi Maluleke')} required autoComplete="name" /></label>
                <label>{t('Town')}<input name="location" value={form.location} onChange={change} placeholder={t('e.g. Polokwane')} autoComplete="address-level2" /></label>
              </>
            )}
            {mode !== 'reset' && (
              <label>{mode === 'register' ? t('Mobile number') : t('contact')}
                <input name="contact" value={form.contact} onChange={change} placeholder="071 234 5678" required autoComplete="username" inputMode={mode === 'register' ? 'tel' : 'text'} />
              </label>
            )}
            {mode === 'register' && (
              <label>{t('Email (optional, for receipts and password reset)')}
                <input name="email" type="email" value={form.email} onChange={change} placeholder="you@example.com" autoComplete="email" />
              </label>
            )}
            {(mode === 'login' || mode === 'register' || mode === 'reset') && !mfa && (
              <label>{mode === 'reset' ? t('New password') : t('password')}
                <input name="password" type="password" value={form.password} onChange={change} placeholder="••••••••" required
                       autoComplete={mode === 'login' ? 'current-password' : 'new-password'} minLength={mode === 'login' ? 1 : 8} />
              </label>
            )}
            {(mode === 'register' || mode === 'reset') && form.password && (
              <p className={`hint ${strong(form.password) ? 'ok' : ''}`}>{strong(form.password) ? t('✓ Strong enough') : t('At least 8 characters, with a letter and a number')}</p>
            )}
            {mfa && (
              <label>{t('Authenticator code')}<input name="otp" value={form.otp} onChange={change} inputMode="numeric" pattern="\d{6}" maxLength={6} placeholder="123456" required autoFocus autoComplete="one-time-code" /></label>
            )}
            {mode === 'register' && (
              <label className="check">
                <input type="checkbox" name="acceptTerms" checked={form.acceptTerms} onChange={change} />
                <span>{t('I accept the terms of use and the privacy notice. My information is processed under POPIA and I can download or delete it at any time.')}</span>
              </label>
            )}
            {error && <div className="error" role="alert">{error}</div>}
            {info && <div className="notice inline" role="status">{info}</div>}
            {devLink && <div className="notice inline">{t('Development mode:')} <a href={devLink}>{t('open the reset link')}</a></div>}
            <button className={`primary full ${loading ? 'is-loading' : ''}`} disabled={loading}>
              {loading ? t('Please wait…') : mode === 'login' ? (mfa ? t('Verify and sign in') : t('signIn')) : mode === 'register' ? t('createAccount')
                : mode === 'forgot' ? t('Send reset link') : t('Save new password')} <span className="arrow">→</span>
            </button>
          </form>
          {mode === 'login' && <button className="switch" onClick={() => switchTo('forgot')}>{t('forgot')}</button>}
          <button className="switch" onClick={() => switchTo(mode === 'login' ? 'register' : 'login')}>
            {mode === 'login' ? t('New here? Create an account') : t('Back to sign in')}
          </button>
        </div>
        <p className="footer-note">{t('Built for South African agriculture · POPIA-aware · Works on low data')}</p>
      </div>
    </div>
  );
}
