import { useEffect, useState } from 'react';
import { authApi, farmerApi, notificationApi, privacyApi } from '../lib/api';
import { SkeletonLines } from '../components/Skeleton';
import Badge from '../components/Badge';
import { useToast } from '../components/Toast';
import useLang from '../hooks/useLang';
import { LANGUAGES } from '../lib/i18n';
import { dateTime, titleCase } from '../lib/format';
import useTheme from '../hooks/useTheme';

const strong = (p) => p.length >= 8 && /[A-Za-z]/.test(p) && /\d/.test(p);

function Section({ title, eyebrow, children }) {
  const { t } = useLang();
  return (
    <section className="panel settings-section">
      <div className="panel-head"><div><p className="eyebrow">{t(eyebrow)}</p><h2>{t(title)}</h2></div></div>
      {children}
    </section>
  );
}

function Profile({ farmer, onFarmerChange }) {
  const toast = useToast();
  const { lang, setLang, t } = useLang();
  const [f, setF] = useState({ name: farmer.name || '', location: farmer.location || '', email: farmer.email || '', preferredLanguage: farmer.preferredLanguage || lang || 'en' });
  const [busy, setBusy] = useState(false);
  const save = async (e) => {
    e.preventDefault(); setBusy(true);
    try {
      const me = await farmerApi.updateMe({ ...f, email: f.email || null });
      onFarmerChange?.(me); setLang(f.preferredLanguage); toast.success(t('Profile saved.'));
    } catch (err) { toast.error(err.message); }
    setBusy(false);
  };
  return (
    <Section eyebrow="YOU" title="Profile">
      <form className="form-grid" onSubmit={save}>
        <label>{t('Name')}<input value={f.name} onChange={(e) => setF({ ...f, name: e.target.value })} required /></label>
        <label>{t('Town')}<input value={f.location} onChange={(e) => setF({ ...f, location: e.target.value })} /></label>
        <label>{t('Mobile number')}<input value={farmer.contact || ''} disabled /></label>
        <label>{t('Email (for receipts and password reset)')}<input type="email" value={f.email} onChange={(e) => setF({ ...f, email: e.target.value })} /></label>
        <label>{t('language')}
          <select value={f.preferredLanguage} onChange={(e) => setF({ ...f, preferredLanguage: e.target.value })}>
            {LANGUAGES.map(([code, name]) => <option key={code} value={code}>{name}</option>)}
          </select>
        </label>
        <label>{t('Account type')}<input value={titleCase(farmer.role || 'FARMER')} disabled /></label>
        <div className="form-actions"><button className="primary" disabled={busy}>{t('Save profile')}</button></div>
      </form>
    </Section>
  );
}

function Notifications() {
  const toast = useToast();
  const { t } = useLang();
  const [p, setP] = useState(null);
  useEffect(() => { notificationApi.preferences().then(setP).catch((e) => toast.error(e.message)); /* eslint-disable-next-line */ }, []);
  const toggle = async (key) => {
    const next = { ...p, [key]: !p[key] };
    setP(next);
    try { setP(await notificationApi.savePreferences(next)); } catch (e) { toast.error(e.message); setP(p); }
  };
  if (!p) return <Section eyebrow="ALERTS" title="Notifications"><SkeletonLines lines={3} /></Section>;
  const row = (key, label, hint, available = true) => (
    <label className="check toggle-row" key={key}>
      <input type="checkbox" checked={!!p[key]} disabled={!available || key === 'inApp'} onChange={() => toggle(key)} />
      <span><b>{t(label)}</b><br /><small className="muted">{available ? t(hint) : t('Not set up on this server yet. Ask the administrator.')}</small></span>
    </label>
  );
  return (
    <Section eyebrow="ALERTS" title="Notifications">
      <p className="muted">{t('Order updates, disputes, compliance deadlines and export-rule changes. In-app notifications are always on.')}</p>
      {row('inApp', 'In the app', 'The bell at the top of the screen, updated live.')}
      {row('email', 'Email', 'Needs an email address on your profile.', p.emailAvailable)}
      {row('sms', 'SMS', 'Short alerts for urgent things such as new orders and deadlines tomorrow.', p.smsAvailable)}
      {row('whatsapp', 'WhatsApp', 'Same alerts by WhatsApp.', p.smsAvailable)}
    </Section>
  );
}

function Security({ farmer, onFarmerChange, onLogout }) {
  const toast = useToast();
  const { t } = useLang();
  const [pw, setPw] = useState({ current: '', next: '' });
  const [mfa, setMfa] = useState(null);
  const [code, setCode] = useState('');
  const [busy, setBusy] = useState(false);
  const run = async (fn) => { setBusy(true); try { await fn(); } catch (e) { toast.error(e.message); } setBusy(false); };

  const change = (e) => {
    e.preventDefault();
    if (!strong(pw.next)) { toast.error(t('Use at least 8 characters with a letter and a number.')); return; }
    run(async () => { await authApi.changePassword(pw.current, pw.next); setPw({ current: '', next: '' }); toast.success(t('Password changed.')); });
  };
  return (
    <Section eyebrow="SECURITY" title="Sign-in and security">
      <form className="form-grid" onSubmit={change}>
        <label>{t('Current password')}<input type="password" autoComplete="current-password" value={pw.current} onChange={(e) => setPw({ ...pw, current: e.target.value })} required /></label>
        <label>{t('New password')}<input type="password" autoComplete="new-password" value={pw.next} onChange={(e) => setPw({ ...pw, next: e.target.value })} required minLength={8} /></label>
        {pw.next && <p className={`hint span-all ${strong(pw.next) ? 'ok' : ''}`}>{strong(pw.next) ? t('✓ Strong enough') : t('At least 8 characters, with a letter and a number')}</p>}
        <div className="form-actions"><button className="secondary" disabled={busy}>{t('Change password')}</button></div>
      </form>
      <hr />
      <div className="row-wrap" style={{ justifyContent: 'space-between' }}>
        <div><b>{t('Two-step sign-in')}</b> <Badge tone={farmer.mfaEnabled ? 'good' : 'neutral'}>{farmer.mfaEnabled ? 'On' : 'Off'}</Badge>
          <p className="muted">{t('A 6-digit code from an authenticator app (Google Authenticator, Microsoft Authenticator, Authy) as well as your password.')}{farmer.role === 'ADMIN' && ` ${t('Strongly recommended for administrators.')}`}</p></div>
        {!farmer.mfaEnabled && !mfa && <button className="secondary" disabled={busy} onClick={() => run(async () => setMfa(await authApi.mfaSetup()))}>{t('Turn on')}</button>}
      </div>
      {mfa && !farmer.mfaEnabled && (
        <div className="mfa-setup">
          <ol>
            <li>{t('On your phone, open your authenticator app and add an account.')} <a href={mfa.otpauthUri}>{t('Tap here if you are on your phone')}</a>{t(', or enter this key by hand:')}</li>
          </ol>
          <code className="secret">{mfa.secret.replace(/(.{4})/g, '$1 ').trim()}</code>
          <ol start={2}><li>{t('Type the 6-digit code the app shows:')}</li></ol>
          <div className="row-wrap">
            <input className="filter" inputMode="numeric" maxLength={6} placeholder="123456" value={code} onChange={(e) => setCode(e.target.value.replace(/\D/g, ''))} />
            <button className="primary" disabled={busy || code.length !== 6} onClick={() => run(async () => {
              await authApi.mfaEnable(code); onFarmerChange?.({ mfaEnabled: true }); setMfa(null); setCode(''); toast.success(t('Two-step sign-in is on.'));
            })}>{t('Confirm')}</button>
          </div>
        </div>
      )}
      {farmer.mfaEnabled && (
        <div className="row-wrap">
          <input className="filter" inputMode="numeric" maxLength={6} placeholder={t('Current code to turn off')} value={code} onChange={(e) => setCode(e.target.value.replace(/\D/g, ''))} />
          <button className="link-btn danger" disabled={busy || code.length !== 6} onClick={() => run(async () => {
            await authApi.mfaDisable(code); onFarmerChange?.({ mfaEnabled: false }); setCode(''); toast.success(t('Two-step sign-in is off.'));
          })}>{t('Turn off two-step sign-in')}</button>
        </div>
      )}
      <hr />
      <div className="row-wrap" style={{ justifyContent: 'space-between' }}>
        <div><b>{t('Sign out everywhere')}</b><p className="muted">{t('Lost a phone or used a shared computer? This signs you out on every device.')}</p></div>
        <button className="secondary" onClick={() => window.confirm(t('Sign out on all devices, including this one?')) && run(async () => { await authApi.logoutAll(); onLogout?.(); })}>{t('Sign out everywhere')}</button>
      </div>
    </Section>
  );
}

function Privacy() {
  const toast = useToast();
  const { t } = useLang();
  const [consents, setConsents] = useState(null);
  const [requests, setRequests] = useState([]);
  const [reason, setReason] = useState('');
  const load = () => {
    privacyApi.consents().then(setConsents).catch((e) => toast.error(e.message));
    privacyApi.requests().then(setRequests).catch(() => {});
  };
  useEffect(() => { load(); /* eslint-disable-next-line */ }, []);
  const toggle = async (c) => {
    if (['TERMS', 'PRIVACY'].includes(c.purpose) && c.granted) {
      toast.info(t('The terms and privacy notice are needed to use the platform. To stop, request deletion of your account below.'));
      return;
    }
    try { await privacyApi.setConsent(c.purpose, !c.granted); load(); } catch (e) { toast.error(e.message); }
  };
  const del = async () => {
    if (!window.confirm(t('Ask us to delete your account and personal information? An administrator completes the request. Records the law requires us to keep (such as completed sales for tax) are kept without your personal details.'))) return;
    try { await privacyApi.requestDeletion(reason); setReason(''); toast.success(t('Request received. We will confirm when it is done.')); load(); } catch (e) { toast.error(e.message); }
  };
  const openDeletion = requests.find((r) => r.status === 'OPEN');
  return (
    <Section eyebrow="POPIA" title="Your information">
      <p className="muted">{t('You decide how your information is used. Every change is recorded with the date and the version of the notice.')}</p>
      {!consents ? <SkeletonLines lines={4} /> : consents.map((c) => (
        <label key={c.purpose} className="check toggle-row">
          <input type="checkbox" checked={c.granted} onChange={() => toggle(c)} />
          <span><b>{t(c.label)}</b><br /><small className="muted">{c.at ? t(c.granted ? 'Agreed {when} · version {version}' : 'Withdrawn {when} · version {version}', { when: dateTime(c.at), version: c.version }) : t('Not given')}</small></span>
        </label>
      ))}
      <hr />
      <div className="row-wrap" style={{ justifyContent: 'space-between' }}>
        <div><b>{t('Download my data')}</b><p className="muted">{t('Everything we hold about you, in one file (JSON).')}</p></div>
        <button className="secondary" onClick={() => privacyApi.exportData().catch((e) => toast.error(e.message))}>{t('download')}</button>
      </div>
      <hr />
      <b>{t('Delete my account')}</b>
      {openDeletion ? <p className="notice inline">{t('Deletion requested on {when}. An administrator will complete it.', { when: dateTime(openDeletion.createdAt) })}</p> : (
        <div className="stack">
          <textarea rows={2} placeholder={t('Reason (optional)')} value={reason} onChange={(e) => setReason(e.target.value)} />
          <button className="link-btn danger" onClick={del}>{t('Request account deletion')}</button>
        </div>
      )}
    </Section>
  );
}

function Display() {
  const { t } = useLang();
  const { theme, textSize, setTheme, setTextSize } = useTheme();
  const options = [
    ['light', 'Light', 'Bright background, dark text.', '☀'],
    ['dark', 'Dark', 'Dark background that is easier on the eyes, especially at night.', '☾'],
    ['system', 'Same as my device', 'Follows the light or dark setting on your phone or computer.', '◐']
  ];
  return (
    <Section eyebrow="DISPLAY" title="Theme and text size">
      <p className="muted">{t('Choose what is most comfortable for your eyes. Saved on this device.')}</p>
      <div className="theme-picker" role="radiogroup" aria-label={t('Theme')}>
        {options.map(([value, label, hint, mark]) => (
          <button key={value} type="button" role="radio" aria-checked={theme === value}
                  className={`theme-option theme-${value} ${theme === value ? 'on' : ''}`} onClick={() => setTheme(value)}>
            <span className="theme-preview" aria-hidden="true"><i /><i /><i /></span>
            <b><span aria-hidden="true">{mark}</span> {t(label)}</b>
            <small>{t(hint)}</small>
          </button>
        ))}
      </div>
      <label className="check toggle-row">
        <input type="checkbox" checked={textSize === 'large'} onChange={(e) => setTextSize(e.target.checked ? 'large' : 'normal')} />
        <span><b>{t('Larger text')}</b><br /><small className="muted">{t('Makes words and buttons bigger across the whole app.')}</small></span>
      </label>
    </Section>
  );
}

export default function Settings({ farmer, onFarmerChange, onLogout }) {
  const { t } = useLang();
  return (
    <>
      <div className="page-head">
        <div>
          <p className="eyebrow">{t('SETTINGS')}</p>
          <h1>{t('Your account.')}</h1>
          <p className="muted">{t('Profile, language, display, alerts, security and privacy.')}</p>
        </div>
      </div>
      <div className="fm-body settings">
        <Profile farmer={farmer} onFarmerChange={onFarmerChange} />
        <Display />
        <Notifications />
        <Security farmer={farmer} onFarmerChange={onFarmerChange} onLogout={onLogout} />
        <Privacy />
      </div>
    </>
  );
}
