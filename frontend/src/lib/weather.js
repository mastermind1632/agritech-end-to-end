// Weather from Open-Meteo (free, no API key). The farm's town is geocoded once and cached, and the forecast is
// cached for an hour so the dashboard works on patchy data.
import { tr } from './i18n.js';

const CACHE = 'agritech_weather_v1';

const CODES = {
  0: ['Clear', '☀'], 1: ['Mostly clear', '🌤'], 2: ['Partly cloudy', '⛅'], 3: ['Overcast', '☁'], 45: ['Fog', '🌫'], 48: ['Fog', '🌫'],
  51: ['Light drizzle', '🌦'], 53: ['Drizzle', '🌦'], 55: ['Heavy drizzle', '🌧'], 61: ['Light rain', '🌦'], 63: ['Rain', '🌧'],
  65: ['Heavy rain', '🌧'], 71: ['Light snow', '🌨'], 73: ['Snow', '🌨'], 75: ['Heavy snow', '❄'], 80: ['Showers', '🌦'],
  81: ['Showers', '🌧'], 82: ['Violent showers', '⛈'], 95: ['Thunderstorm', '⛈'], 96: ['Thunderstorm, hail', '⛈'], 99: ['Thunderstorm, hail', '⛈']
};

export const describe = (code) => { const [label, icon] = CODES[code] || ['Weather', '•']; return [tr(label), icon]; };

export async function forecastFor(place) {
  const town = String(place || '').split(',')[0].trim();
  if (!town) throw new Error(tr('Add your town to your profile to see the weather.'));
  try {
    const c = JSON.parse(localStorage.getItem(CACHE) || 'null');
    if (c && c.town === town && Date.now() - c.at < 3600_000) return c.data;
  } catch { /* ignore */ }
  const geo = await fetch(`https://geocoding-api.open-meteo.com/v1/search?name=${encodeURIComponent(town)}&count=1&country=ZA&language=en`).then((r) => r.json());
  const g = geo?.results?.[0];
  if (!g) throw new Error(tr('We could not find "{town}" on the map.', { town }));
  const url = `https://api.open-meteo.com/v1/forecast?latitude=${g.latitude}&longitude=${g.longitude}`
    + '&current=temperature_2m,weather_code,wind_speed_10m,relative_humidity_2m'
    + '&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum,precipitation_probability_max'
    + '&timezone=Africa%2FJohannesburg&forecast_days=5';
  const f = await fetch(url).then((r) => r.json());
  const data = { place: `${g.name}${g.admin1 ? ', ' + g.admin1 : ''}`, current: f.current, daily: f.daily };
  try { localStorage.setItem(CACHE, JSON.stringify({ town, at: Date.now(), data })); } catch { /* ignore */ }
  return data;
}

/** Plain-language farming hint from the next five days. */
export function farmingHint(daily) {
  if (!daily) return '';
  const rain = (daily.precipitation_sum || []).reduce((a, b) => a + (b || 0), 0);
  const hot = Math.max(...(daily.temperature_2m_max || [0]));
  const cold = Math.min(...(daily.temperature_2m_min || [10]));
  if (cold <= 2) return tr('Frost risk this week: protect seedlings and young crops at night.');
  if (rain >= 25) return tr('About {mm} mm of rain expected: hold off on spraying and fertiliser before heavy rain.', { mm: Math.round(rain) });
  if (hot >= 33 && rain < 5) return tr('Hot and dry: irrigate early in the morning and check livestock water.');
  if (rain < 2) return tr('Little rain expected: plan irrigation for the week.');
  return tr('Mild week ahead: a good window for field work.');
}
