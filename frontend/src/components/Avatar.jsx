import { useEffect, useState } from 'react';
import { avatarApi } from '../lib/api';

// farmerId -> { p: Promise<objectUrl | null>, at: timestamp }
const cache = new Map();
const listeners = new Set();
const TTL_MS = 60000;

function load(id) {
  const hit = cache.get(id);
  if (hit && Date.now() - hit.at < TTL_MS) return hit.p;
  const p = avatarApi.fetch(id).then((blob) => (blob ? URL.createObjectURL(blob) : null)).catch(() => null);
  cache.set(id, { p, at: Date.now() });
  return p;
}

// Call after uploading or removing a photo so every Avatar for that farmer reloads.
export function avatarChanged(id) {
  cache.delete(id);
  listeners.forEach((fn) => fn(id));
}

export default function Avatar({ id, name, size = 35, className = '' }) {
  const [url, setUrl] = useState(null);
  const [tick, setTick] = useState(0);

  useEffect(() => {
    const fn = (changed) => { if (changed === id) setTick((t) => t + 1); };
    listeners.add(fn);
    return () => { listeners.delete(fn); };
  }, [id]);

  useEffect(() => {
    let alive = true;
    if (id) load(id).then((u) => { if (alive) setUrl(u); });
    return () => { alive = false; };
  }, [id, tick]);

  const box = { width: size, height: size };
  if (url) return <img className={`avatar-img ${className}`} src={url} alt={name || ''} style={box} />;
  return <div className={`avatar ${className}`} style={{ ...box, fontSize: Math.round(size * 0.4) }}>{(name || 'F').slice(0, 1)}</div>;
}
