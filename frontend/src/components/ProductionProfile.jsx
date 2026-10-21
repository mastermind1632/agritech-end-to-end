import { useEffect, useState } from 'react';
import { intelligenceApi } from '../lib/api';
import useLang from '../hooks/useLang';
import { monthName } from '../lib/i18n';

const EMPTY = { crop: '', province: '', district: '', latitude: '', longitude: '', hectares: '', capacityKg: '',
  plantingMonth: '', harvestMonth: '', deliveryRadiusKm: 100, businessRegistered: false,
  consistentProduction: false, analyticsConsent: false, expenseConsent: false };
const NUMBERS = ['latitude', 'longitude', 'hectares', 'capacityKg', 'plantingMonth', 'harvestMonth', 'deliveryRadiusKm'];

export default function ProductionProfile({ onSaved }) {
  const { t, lang } = useLang();
  const [form, setForm] = useState(EMPTY);
  const [options, setOptions] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [status, setStatus] = useState('');
  useEffect(() => {
    let active = true;
    Promise.all([intelligenceApi.options(), intelligenceApi.profile()]).then(([o, p]) => {
      if (!active) return;
      setOptions(o);
      if (p.profile) setForm(Object.fromEntries(Object.keys(EMPTY).map(k => [k, p.profile[k] ?? EMPTY[k]])));
    }).catch(e => { if (active) setError(e.message); });
    return () => { active = false; };
  }, []);
  const change = e => setForm({ ...form, [e.target.name]: e.target.type === 'checkbox' ? e.target.checked : e.target.value });
  const save = async e => {
    e.preventDefault(); setBusy(true); setError(''); setStatus('');
    try {
      const body = { ...form };
      NUMBERS.forEach(k => { body[k] = Number(body[k]); });
      await intelligenceApi.saveProfile(body);
      setStatus(t('Production profile saved.'));
      onSaved?.();
    } catch (err) { setError(err.message); }
    finally { setBusy(false); }
  };
  const remove = async () => {
    if (!window.confirm(t('Delete your production profile, produce listings and recommendation history?'))) return;
    setBusy(true); setError('');
    try { await intelligenceApi.deleteProfile(); setForm(EMPTY); setStatus(t('Production data deleted.')); }
    catch (err) { setError(err.message); }
    finally { setBusy(false); }
  };
  const gps = () => {
    if (!navigator.geolocation) { setError(t('Location access is unavailable. Enter coordinates manually.')); return; }
    setBusy(true);
    navigator.geolocation.getCurrentPosition(p => {
      setForm(f => ({ ...f, latitude: p.coords.latitude.toFixed(5), longitude: p.coords.longitude.toFixed(5) }));
      setBusy(false);
    }, () => { setError(t('Could not get your location. Enter coordinates manually.')); setBusy(false); }, { timeout: 10000 });
  };
  return <section className="production-section">
    <h2>{t('Farm production')}</h2>
    {error && <p className="error" role="alert">{t(error)}</p>}
    {status && <p className="notice" role="status">{status}</p>}
    {!options && !error && <p aria-live="polite">{t('Loading production profile...')}</p>}
    {options && <form onSubmit={save} className="production-form">
      <label>{t('Primary crop')}<select name="crop" value={form.crop} onChange={change} required><option value="">{t('Select crop')}</option>{options.crops.map(c => <option key={c} value={c}>{t(c)}</option>)}</select></label>
      <label>{t('Province')}<select name="province" value={form.province} onChange={change} required><option value="">{t('Select province')}</option>{options.provinces.map(p => <option key={p}>{p}</option>)}</select></label>
      <label>{t('District')}<input name="district" value={form.district} onChange={change} required minLength={2} maxLength={100} /></label>
      <label>{t('Farm size (ha)')}<input name="hectares" type="number" min="0.001" max="1000000" step="any" value={form.hectares} onChange={change} required /></label>
      <label>{t('Seasonal capacity (kg)')}<input name="capacityKg" type="number" min="0.001" max="1000000000" step="any" value={form.capacityKg} onChange={change} required /></label>
      {['plantingMonth', 'harvestMonth'].map(k => <label key={k}>{k === 'plantingMonth' ? t('Planting month') : t('Harvest month')}<select name={k} value={form[k]} onChange={change} required><option value="">{t('Select month')}</option>{Array.from({ length: 12 }).map((_, i) => <option key={i} value={i + 1}>{monthName(i, lang)}</option>)}</select></label>)}
      <label>{t('Delivery radius (km)')}<input name="deliveryRadiusKm" type="number" min="1" max="2000" value={form.deliveryRadiusKm} onChange={change} required /></label>
      <label>{t('Latitude')}<input name="latitude" type="number" min="-35" max="-22" step="any" value={form.latitude} onChange={change} required /></label>
      <label>{t('Longitude')}<input name="longitude" type="number" min="16" max="33" step="any" value={form.longitude} onChange={change} required /></label>
      <div className="production-checks">
        <label><input type="checkbox" name="businessRegistered" checked={form.businessRegistered} onChange={change} /> {t('Business registered')}</label>
        <label><input type="checkbox" name="consistentProduction" checked={form.consistentProduction} onChange={change} /> {t('Consistent seasonal production')}</label>
        <label><input type="checkbox" name="analyticsConsent" checked={form.analyticsConsent} onChange={change} /> {t('I consent to my production profile and activity being used for shared segmentation and recommendation evaluation.')}</label>
        <label><input type="checkbox" name="expenseConsent" checked={form.expenseConsent} onChange={change} /> {t('I consent to my monthly expense totals being used for private service recommendations. Individual transactions are not shared.')}</label>
      </div>
      <div className="production-actions"><button type="submit" className="primary" disabled={busy}>{busy ? t('Saving...') : t('Save production profile')}</button><button type="button" className="secondary" onClick={gps} disabled={busy || !window.isSecureContext} title={window.isSecureContext ? t('Use browser location') : t('Browser location requires HTTPS; coordinates can be entered manually')}>{t('Use current location')}</button><button type="button" className="secondary" onClick={remove} disabled={busy}>{t('Delete production data')}</button></div>
    </form>}
  </section>;
}
