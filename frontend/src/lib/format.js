// Formatting helpers shared by every page. Dates follow the interface language.
import { getLang, intlLocale, MONTH_NAMES, tr } from './i18n.js';
export const money = (n, decimals = 2) => new Intl.NumberFormat('en-ZA', {
  style: 'currency', currency: 'ZAR', minimumFractionDigits: decimals, maximumFractionDigits: decimals
}).format(Number(n || 0));

export const compactMoney = (n) => {
  const v = Number(n || 0);
  const abs = Math.abs(v);
  if (abs >= 1_000_000) return `R${(v / 1_000_000).toFixed(1)}m`;
  if (abs >= 10_000) return `R${Math.round(v / 1000)}k`;
  return money(v, 0);
};

const pad = (n) => String(n).padStart(2, '0');
const customMonths = () => MONTH_NAMES[getLang()];

export const date = (d) => {
  if (!d) return '';
  const x = new Date(d);
  const m = customMonths();
  if (m) return `${x.getDate()} ${m[x.getMonth()].slice(0, 3)} ${x.getFullYear()}`;
  return x.toLocaleDateString(intlLocale(), { day: 'numeric', month: 'short', year: 'numeric' });
};

export const dateTime = (d) => {
  if (!d) return '';
  const x = new Date(d);
  const m = customMonths();
  if (m) return `${x.getDate()} ${m[x.getMonth()].slice(0, 3)}, ${pad(x.getHours())}:${pad(x.getMinutes())}`;
  return x.toLocaleString(intlLocale(), { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' });
};

export const timeAgo = (d) => {
  if (!d) return '';
  const s = Math.round((Date.now() - new Date(d).getTime()) / 1000);
  if (s < 60) return tr('just now');
  if (s < 3600) return tr('{n} min ago', { n: Math.floor(s / 60) });
  if (s < 86400) return tr('{n} h ago', { n: Math.floor(s / 3600) });
  if (s < 7 * 86400) return tr('{n} d ago', { n: Math.floor(s / 86400) });
  return date(d);
};

export const monthLabel = (ym) => {
  const [y, m] = String(ym).split('-').map(Number);
  if (!y || !m) return ym;
  const custom = customMonths();
  if (custom) return `${custom[m - 1].slice(0, 3)} ${String(y).slice(2)}`;
  return new Date(y, m - 1, 1).toLocaleDateString(intlLocale(), { month: 'short', year: '2-digit' });
};

export const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

const rawTitleCase = (s) => String(s || '').toLowerCase().replace(/_/g, ' ').replace(/\b\w/g, (c) => c.toUpperCase());
/** "IN_PROGRESS" -> "In Progress", translated when the status has a translation. */
export const titleCase = (s) => tr(rawTitleCase(s));

export const bytes = (n) => (n > 1024 * 1024 ? `${(n / 1024 / 1024).toFixed(1)} MB` : `${Math.max(1, Math.round(n / 1024))} KB`);

/** Nice upper bound for a chart axis, e.g. 8 734 -> 10 000. */
export function niceMax(v) {
  if (!(v > 0)) return 1;
  const p = 10 ** Math.floor(Math.log10(v));
  const n = v / p;
  return (n <= 1 ? 1 : n <= 2 ? 2 : n <= 5 ? 5 : 10) * p;
}
