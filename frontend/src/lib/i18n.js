// Interface language.
//
// Two kinds of entries:
//  1. Short keys (D below) used by the menu since the first version, e.g. t('overview').
//  2. Phrases (src/locales/*.js) keyed by the English text itself, e.g. t('Upload statement').
//     Variables are written in braces: t('{done} of {total} steps done', { done: 3, total: 9 }).
//
// Anything without a translation falls back to English, so a missing phrase never breaks a screen.
// Afrikaans, Sepedi, Xitsonga and isiZulu are DRAFT translations: have first-language speakers review
// src/locales before launch (every row shows all four languages side by side to make that easy).
import PHRASES, { LANG_COLUMNS } from '../locales/index.js';

export const LANGUAGES = [
  ['en', 'English'], ['af', 'Afrikaans'], ['nso', 'Sepedi'], ['ts', 'Xitsonga'], ['zu', 'isiZulu']
];

const D = {
  en: {
    overview: 'Overview', companion: 'Business Builder', statements: 'Expense Analysis', finance: 'My Finances',
    export: 'Export Readiness', vault: 'Documents', farmmarket: 'Farm Market', market: 'Supplier Shop',
    groups: 'Group Orders', intelligence: 'Market Insights', ai: 'Buying Tips', chat: 'Chat',
    notifications: 'Notifications', disputes: 'Disputes', settings: 'Settings & Privacy', admin: 'Admin Console',
    supplier: 'Supplier Portal', profile: 'My Profile', signOut: 'Sign out', online: 'Online', offline: 'Offline - showing saved pages',
    farmBusiness: 'FARM BUSINESS', marketplace: 'MARKET', community: 'COMMUNITY', account: 'ACCOUNT',
    goodDay: 'Good day', nextSteps: 'Your next steps', weather: 'Weather', prices: 'Market prices', save: 'Save',
    cancel: 'Cancel', search: 'Search', loading: 'Loading...', upload: 'Upload', download: 'Download', language: 'Language',
    signIn: 'Sign in', createAccount: 'Create account', password: 'Password', contact: 'Mobile number or email',
    forgot: 'Forgot your password?', allCaughtUp: 'You are all caught up', viewAll: 'View all', markAllRead: 'Mark all as read'
  },
  af: {
    overview: 'Oorsig', companion: 'Besigheidsbouer', statements: 'Uitgawe-ontleding', finance: 'My finansies',
    export: 'Uitvoergereedheid', vault: 'Dokumente', farmmarket: 'Plaasmark', market: 'Verskafferwinkel',
    groups: 'Groepbestellings', intelligence: 'Markinsigte', ai: 'Koopwenke', chat: 'Gesels',
    notifications: 'Kennisgewings', disputes: 'Geskille', settings: 'Instellings en privaatheid', admin: 'Administrasie',
    supplier: 'Verskafferportaal', profile: 'My profiel', signOut: 'Teken uit', online: 'Aanlyn', offline: 'Vanlyn - gestoorde bladsye',
    farmBusiness: 'PLAASBESIGHEID', marketplace: 'MARK', community: 'GEMEENSKAP', account: 'REKENING',
    goodDay: 'Goeiedag', nextSteps: 'Jou volgende stappe', weather: 'Weer', prices: 'Markpryse', save: 'Stoor',
    cancel: 'Kanselleer', search: 'Soek', loading: 'Laai...', upload: 'Laai op', download: 'Laai af', language: 'Taal',
    signIn: 'Teken in', createAccount: 'Skep rekening', password: 'Wagwoord', contact: 'Selnommer of e-pos',
    forgot: 'Wagwoord vergeet?', allCaughtUp: 'Alles is op datum', viewAll: 'Sien alles', markAllRead: 'Merk alles as gelees'
  },
  nso: {
    overview: 'Kakaretšo', companion: 'Mohlami wa Kgwebo', statements: 'Tshekatsheko ya Ditshenyegelo', finance: 'Ditšhelete tša ka',
    export: 'Go itokišetša go Romela Ntle', vault: 'Ditokumente', farmmarket: 'Mmaraka wa Polasa', market: 'Lebenkele la Bafepi',
    groups: 'Diodara tša Sehlopha', intelligence: 'Tsebo ya Mmaraka', ai: 'Maele a go Reka', chat: 'Poledišano',
    notifications: 'Ditsebišo', disputes: 'Dingangišano', settings: 'Dipeakanyo le Sephiri', admin: 'Taolo',
    supplier: 'Portale ya Mofepi', profile: 'Profaele ya ka', signOut: 'Tšwa', online: 'O inthaneteng', offline: 'Ga o inthaneteng',
    farmBusiness: 'KGWEBO YA POLASA', marketplace: 'MMARAKA', community: 'SETŠHABA', account: 'AKHAONTE',
    goodDay: 'Dumela', nextSteps: 'Magato a gago a latelago', weather: 'Boso', prices: 'Ditheko tša mmaraka', save: 'Boloka',
    cancel: 'Khansela', search: 'Nyaka', loading: 'E a laiša...', upload: 'Romela', download: 'Laolla', language: 'Polelo',
    signIn: 'Tsena', createAccount: 'Bula akhaonte', password: 'Lentšuphetišo', contact: 'Nomoro ya selefoune goba imeile',
    forgot: 'O lebetše lentšuphetišo?', allCaughtUp: 'Ga go sa na selo se seswa', viewAll: 'Bona tšohle', markAllRead: 'Swaya tšohle bjalo ka tše badilwego'
  },
  ts: {
    overview: 'Nkatsakanyo', companion: 'Muakhi wa Bindzu', statements: 'Nxopaxopo wa Swihlawulo', finance: 'Timali ta mina',
    export: 'Ku Tilulamisela ku Xavisa Ehandle', vault: 'Matsalwa', farmmarket: 'Makete wa Purasi', market: 'Xitolo xa Vaphakeri',
    groups: 'Tioda ta Ntlawa', intelligence: 'Vutivi bya Makete', ai: 'Switsundzuxo swa ku Xava', chat: 'Bula',
    notifications: 'Switiviso', disputes: 'Timholovo', settings: 'Swiyimiso na Xihundla', admin: 'Vulawuri',
    supplier: 'Photali ya Muphakeri', profile: 'Profayili ya mina', signOut: 'Huma', online: 'U le ka inthanete', offline: 'A wu le ka inthanete',
    farmBusiness: 'BINDZU RA PURASI', marketplace: 'MAKETE', community: 'VAAKATIKO', account: 'AKHAWUNTI',
    goodDay: 'Avuxeni', nextSteps: 'Magoza ya wena lama landzelaka', weather: 'Maxelo', prices: 'Minxavo ya makete', save: 'Hlayisa',
    cancel: 'Khansela', search: 'Lava', loading: 'Ku layicha...', upload: 'Rhumela', download: 'Kopela', language: 'Ririmi',
    signIn: 'Nghena', createAccount: 'Pfula akhawunti', password: 'Phasiwedi', contact: 'Nomboro ya selfoni kumbe imeyili',
    forgot: 'U rivale phasiwedi?', allCaughtUp: 'A ku na nchumu lowuntshwa', viewAll: 'Vona hinkwaswo', markAllRead: 'Funga hinkwaswo tanihi leswi hlayiweke'
  },
  zu: {
    overview: 'Ukubuka konke', companion: 'Umakhi weBhizinisi', statements: 'Ukuhlaziywa Kwezindleko', finance: 'Izimali zami',
    export: 'Ukulungela Ukuthekelisa', vault: 'Imibhalo', farmmarket: 'Imakethe yePulazi', market: 'Isitolo Sabahlinzeki',
    groups: 'Ama-oda Eqembu', intelligence: 'Ulwazi Lwemakethe', ai: 'Amathiphu Okuthenga', chat: 'Xoxa',
    notifications: 'Izaziso', disputes: 'Izingxabano', settings: 'Izilungiselelo nobumfihlo', admin: 'Ukuphatha',
    supplier: 'Iphothali Yomhlinzeki', profile: 'Iphrofayela yami', signOut: 'Phuma', online: 'Uxhumekile', offline: 'Awuxhumekile',
    farmBusiness: 'IBHIZINISI LEPULAZI', marketplace: 'IMAKETHE', community: 'UMPHAKATHI', account: 'I-AKHAWUNTI',
    goodDay: 'Sawubona', nextSteps: 'Izinyathelo zakho ezilandelayo', weather: 'Isimo sezulu', prices: 'Amanani emakethe', save: 'Gcina',
    cancel: 'Khansela', search: 'Sesha', loading: 'Iyalayisha...', upload: 'Layisha', download: 'Landa', language: 'Ulimi',
    signIn: 'Ngena', createAccount: 'Vula i-akhawunti', password: 'Iphasiwedi', contact: 'Inombolo yeselula noma i-imeyili',
    forgot: 'Ukhohlwe iphasiwedi?', allCaughtUp: 'Akukho okusha', viewAll: 'Buka konke', markAllRead: 'Maka konke njengokufundiwe'
  }
};

const KEY = 'agritech_lang';
export const listeners = new Set();
let current = null;

export function getLang() {
  if (current) return current;
  try { current = localStorage.getItem(KEY) || 'en'; } catch { current = 'en'; }
  return current;
}

export function setLang(lang) {
  current = lang;
  try { localStorage.setItem(KEY, lang); } catch { /* ignore */ }
  if (typeof document !== 'undefined') document.documentElement.lang = lang;
  listeners.forEach((fn) => fn(lang));
}

function fill(text, vars) {
  if (!vars) return text;
  return text.replace(/\{(\w+)\}/g, (m, k) => (vars[k] === undefined || vars[k] === null ? m : String(vars[k])));
}

/** Translate a short key or an English phrase; falls back to English, then to the text itself. */
export function translate(lang, key, vars) {
  if (key === undefined || key === null || key === '') return key;
  const k = String(key);
  let s = D[lang] && D[lang][k];
  if (!s && lang !== 'en') {
    const row = PHRASES[k];
    const col = LANG_COLUMNS[lang];
    if (row && col !== undefined) s = row[col];
  }
  if (!s) s = D.en[k] || k;
  return fill(s, vars);
}

/** Translate in the current language. For helpers outside React components (they still re-render on change). */
export const tr = (key, vars) => translate(getLang(), key, vars);

/** True when a phrase has a translation table entry (used by components that translate their props). */
export const known = (key) => typeof key === 'string' && (Object.prototype.hasOwnProperty.call(PHRASES, key) || Object.prototype.hasOwnProperty.call(D.en, key));

// Dates and numbers. Afrikaans and isiZulu are in the browser's locale data; Sepedi and Xitsonga
// month names are supplied here because most browsers do not ship them.
const INTL = { en: 'en-ZA', af: 'af-ZA', zu: 'zu-ZA', nso: 'en-ZA', ts: 'en-ZA' };
export const intlLocale = (lang = getLang()) => INTL[lang] || 'en-ZA';

export const MONTH_NAMES = {
  nso: ['Janaware', 'Feberware', 'Matšhe', 'Aporele', 'Mei', 'June', 'Julae', 'Agostose', 'Setemere', 'Oktoboro', 'Nofemere', 'Disemere'],
  ts: ['Sunguti', 'Nyenyenyani', 'Nyenyankulu', 'Dzivamisoko', 'Mudyaxihi', 'Khotavuxika', 'Mawuwani', 'Mhawuri', 'Ndzhati', 'Nhlangula', 'Hukuri', "N'wendzamhala"]
};
export const DAY_NAMES = {
  nso: ['Lam', 'Mos', 'Bed', 'Rar', 'Ne', 'Hla', 'Mok'],
  ts: ['Son', 'Mus', 'Bir', 'Har', 'Ne', 'Tlh', 'Mug']
};

export function monthName(index, lang = getLang(), short = false) {
  const custom = MONTH_NAMES[lang];
  if (custom) return short ? custom[index].slice(0, 3) : custom[index];
  return new Date(2024, index, 1).toLocaleDateString(intlLocale(lang), { month: short ? 'short' : 'long' });
}

export function weekdayShort(d, lang = getLang()) {
  const custom = DAY_NAMES[lang];
  const date = new Date(d);
  if (custom) return custom[date.getDay()];
  return date.toLocaleDateString(intlLocale(lang), { weekday: 'short' });
}

export const DICTIONARIES = D;
export { PHRASES };
