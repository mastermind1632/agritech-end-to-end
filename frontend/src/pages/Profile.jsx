import { useRef, useState } from 'react';
import Avatar, { avatarChanged } from '../components/Avatar';
import { avatarApi } from '../lib/api';
import ProductionProfile from '../components/ProductionProfile';
import '../market-intelligence.css';
import useLang from '../hooks/useLang';
import { tr } from '../lib/i18n';
import { ROLE_LABEL } from '../components/Layout';

const OK_TYPES = ['image/jpeg', 'image/png', 'image/webp'];

// Scales the photo down to at most 512px and re-encodes it as JPEG (small upload, metadata stripped).
async function prepare(file) {
  const bmp = await createImageBitmap(file);
  const scale = Math.min(1, 512 / Math.max(bmp.width, bmp.height));
  const canvas = document.createElement('canvas');
  canvas.width = Math.round(bmp.width * scale);
  canvas.height = Math.round(bmp.height * scale);
  const ctx = canvas.getContext('2d');
  ctx.fillStyle = '#ffffff';
  ctx.fillRect(0, 0, canvas.width, canvas.height);
  ctx.drawImage(bmp, 0, 0, canvas.width, canvas.height);
  const blob = await new Promise((res) => canvas.toBlob(res, 'image/jpeg', 0.88));
  if (!blob) throw new Error(tr('We could not read that photo. Please try a different one.'));
  return new File([blob], 'avatar.jpg', { type: 'image/jpeg' });
}

export default function Profile({ farmer, onProductionSaved, setPage }) {
  const { t } = useLang();
  const input = useRef(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [ok, setOk] = useState('');

  const pick = async (e) => {
    const file = e.target.files && e.target.files[0];
    e.target.value = '';
    if (!file) return;
    setError(''); setOk('');
    if (!OK_TYPES.includes(file.type)) { setError(t('Please choose a JPEG, PNG or WebP image.')); return; }
    setBusy(true);
    try {
      await avatarApi.upload(farmer.id, await prepare(file));
      avatarChanged(farmer.id);
      setOk(t('Profile photo updated.'));
    } catch (err) {
      setError(err.message || t('Upload failed'));
    } finally {
      setBusy(false);
    }
  };

  const remove = async () => {
    setError(''); setOk(''); setBusy(true);
    try {
      await avatarApi.remove(farmer.id);
      avatarChanged(farmer.id);
      setOk(t('Profile photo removed.'));
    } catch (err) {
      setError(err.message || t('Could not remove photo'));
    } finally {
      setBusy(false);
    }
  };

  return (
    <>
      <div className="page-head">
        <div>
          <p className="eyebrow">{t('account')}</p>
          <h1>{t('Farmer profile')}</h1>
          <p className="muted">{t('Your photo and what you produce. Change your name, language, alerts, password and privacy choices in Settings.')}</p>
        </div>
        <button className="secondary" onClick={() => setPage?.('settings')}>{t('Account settings')}</button>
      </div>
      {ok && <div className="notice">{ok}</div>}
      {error && <div className="error" style={{ margin: '0 48px 16px' }}>{error}</div>}
      <section className="profile-card">
        <Avatar id={farmer.id} name={farmer.name} size={96} />
        <div>
          <span className="tag">{t('{role} ACCOUNT', { role: t(ROLE_LABEL[farmer.role || 'FARMER'] || farmer.role).toUpperCase() })}</span>
          <h2>{farmer.name}</h2>
          <p>{farmer.location || t('Location not provided')} | {farmer.contact}</p>
          <div className="profile-actions">
            <input ref={input} type="file" accept="image/jpeg,image/png,image/webp" hidden onChange={pick} />
            <button className="primary" disabled={busy} onClick={() => input.current.click()}>
              {busy ? t('Working...') : t('Upload photo')}
            </button>
            <button className="secondary" disabled={busy} onClick={remove}>{t('Remove photo')}</button>
          </div>
        </div>
      </section>
      {(!farmer.role || farmer.role === 'FARMER') && <ProductionProfile onSaved={onProductionSaved} />}
    </>
  );
}
