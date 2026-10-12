import { api, TOKEN_KEY } from './api';

const BASE = import.meta.env.VITE_API_URL || '/api';

export const imageUrl = (id) => `${BASE}/market/images/${id}`;

export const orderApi = {
  place: (body) => api('/market/orders', { method: 'POST', body: JSON.stringify(body) }),
  buying: () => api('/market/orders/buying'),
  selling: () => api('/market/orders/selling'),
  summary: () => api('/market/orders/sales-summary'),
  act: (id, action, body) => api(`/market/orders/${id}/${action}`, { method: 'POST', body: JSON.stringify(body || {}) })
};

export const reviewApi = {
  order: (orderId, body) => api(`/market/reviews/order/${orderId}`, { method: 'POST', body: JSON.stringify(body) }),
  supplier: (supplierId, body) => api(`/market/reviews/supplier/${supplierId}`, { method: 'POST', body: JSON.stringify(body) }),
  summary: (type, id) => api(`/market/reviews/${type}/${encodeURIComponent(id)}`),
  ratings: (type, ids) => (ids.length ? api(`/market/reviews/ratings?type=${type}&ids=${ids.map(encodeURIComponent).join(',')}`) : Promise.resolve({}))
};

export const deleteListingImage = (listingId, imageId) =>
  api(`/market/listings/${listingId}/images/${imageId}`, { method: 'DELETE' });

export async function uploadListingImage(listingId, blob) {
  const fd = new FormData();
  fd.append('file', blob, 'photo.jpg');
  const res = await fetch(`${BASE}/market/listings/${listingId}/images`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${localStorage.getItem(TOKEN_KEY)}` },
    body: fd
  });
  if (!res.ok) {
    let msg = 'We could not upload that photo. Please try again.';
    try { msg = (await res.json()).message || msg; } catch { /* keep default */ }
    throw new Error(msg);
  }
  return res.json();
}

/** Shrinks a phone photo (often 4+ MB) to about 1280px JPEG before upload, so it is quick on poor data. */
export function shrinkImage(file, max = 1280, quality = 0.82) {
  return new Promise((resolve, reject) => {
    const img = new Image();
    const url = URL.createObjectURL(file);
    img.onload = () => {
      URL.revokeObjectURL(url);
      const scale = Math.min(1, max / Math.max(img.width, img.height));
      const canvas = document.createElement('canvas');
      canvas.width = Math.round(img.width * scale);
      canvas.height = Math.round(img.height * scale);
      canvas.getContext('2d').drawImage(img, 0, 0, canvas.width, canvas.height);
      canvas.toBlob((b) => (b ? resolve(b) : reject(new Error('Could not process that photo'))), 'image/jpeg', quality);
    };
    img.onerror = () => { URL.revokeObjectURL(url); reject(new Error('That file is not a photo this browser can read')); };
    img.src = url;
  });
}

/** Straight-line distance in km. */
export function haversineKm(a, b) {
  const R = 6371;
  const rad = (d) => (d * Math.PI) / 180;
  const dLat = rad(b.lat - a.lat);
  const dLng = rad(b.lng - a.lng);
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(rad(a.lat)) * Math.cos(rad(b.lat)) * Math.sin(dLng / 2) ** 2;
  return 2 * R * Math.asin(Math.sqrt(h));
}

/**
 * Real road distance and time from the public OpenStreetMap routing server (OSRM demo).
 * It is a free shared service, so it is only called when the user presses a button.
 * If it is unreachable we fall back to a rough estimate (road is about 1.3x the straight line, ~60 km/h).
 */
export async function drivingEstimate(from, to) {
  try {
    const url = `https://router.project-osrm.org/route/v1/driving/${from.lng},${from.lat};${to.lng},${to.lat}?overview=false`;
    const res = await fetch(url);
    const data = await res.json();
    if (data.code !== 'Ok' || !data.routes?.length) throw new Error('no route');
    return { km: data.routes[0].distance / 1000, minutes: data.routes[0].duration / 60, rough: false };
  } catch {
    const km = haversineKm(from, to) * 1.3;
    return { km, minutes: (km / 60) * 60, rough: true };
  }
}

export const formatMinutes = (m) => (m < 60 ? `${Math.round(m)} min` : `${Math.floor(m / 60)} h ${Math.round(m % 60)} min`);

/** Finds a place in South Africa by name using OpenStreetMap's Nominatim (free; one request per button press). */
export async function geocode(query) {
  const res = await fetch(`https://nominatim.openstreetmap.org/search?format=json&limit=1&countrycodes=za&q=${encodeURIComponent(query)}`,
    { headers: { Accept: 'application/json' } });
  if (!res.ok) throw new Error('Place search is busy right now. Please try again in a moment.');
  const data = await res.json();
  if (!data.length) throw new Error('We could not find that place. Try the name of a nearby town.');
  return { lat: Number(data[0].lat), lng: Number(data[0].lon), label: data[0].display_name };
}

/** Browser location. It only works on https or localhost, so on a phone using http://192.168... it will be blocked. */
export function getPosition(onOk, onFail) {
  if (!navigator.geolocation) return onFail('Your browser cannot share your location. Type a town instead.');
  navigator.geolocation.getCurrentPosition(
    (p) => onOk(p.coords.latitude, p.coords.longitude),
    () => onFail('We could not get your location. Allow location access in your browser, or type a town instead.')
  );
}
