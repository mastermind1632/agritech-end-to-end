/* AgriTech service worker: makes the app open on weak or no signal.
   - The app shell (HTML, JS, CSS, icons) is cached and served cache-first for hashed assets.
   - Page navigations try the network first and fall back to the cached shell when offline.
   - Read-only API calls (GET) fall back to the last cached answer when offline, so farmers can still
     see their checklist, deadlines, orders and prices. Live streams, files, auth and anything that
     changes data always go to the network and are never cached. */
const VERSION = 'agritech-v1';
const SHELL = ['/', '/index.html', '/manifest.webmanifest', '/icons/icon-192.png', '/icons/icon-512.png'];
const API_CACHE = `${VERSION}-api`;
const NEVER_CACHE = ['/api/live', '/api/auth', '/api/farmers/login', '/api/farmers/register', '/api/vault', '/api/privacy/export',
  '/api/statements/export', '/api/finance/export', '/api/companion/plan/export', '/api/admin'];

self.addEventListener('install', (event) => {
  event.waitUntil(caches.open(VERSION).then((c) => c.addAll(SHELL)).then(() => self.skipWaiting()));
});

self.addEventListener('activate', (event) => {
  event.waitUntil(caches.keys()
    .then((keys) => Promise.all(keys.filter((k) => !k.startsWith(VERSION)).map((k) => caches.delete(k))))
    .then(() => self.clients.claim()));
});

self.addEventListener('message', (event) => {
  // Sent on sign-out so the next person on a shared phone never sees the previous user's cached data.
  if (event.data === 'clear-api-cache') caches.delete(API_CACHE);
});

self.addEventListener('fetch', (event) => {
  const req = event.request;
  if (req.method !== 'GET') return;
  const url = new URL(req.url);
  if (url.origin !== self.location.origin) return;

  if (url.pathname.startsWith('/api/')) {
    if (NEVER_CACHE.some((p) => url.pathname.startsWith(p)) || req.headers.get('accept')?.includes('text/event-stream')) return;
    event.respondWith(fetch(req).then((res) => {
      if (res.ok && (res.headers.get('content-type') || '').includes('application/json')) {
        const copy = res.clone();
        caches.open(API_CACHE).then((c) => c.put(req, copy));
      }
      return res;
    }).catch(() => caches.match(req).then((hit) => hit || new Response(JSON.stringify({ message: 'You are offline and this page has not been opened before.' }),
      { status: 503, headers: { 'Content-Type': 'application/json' } }))));
    return;
  }

  if (req.mode === 'navigate') {
    event.respondWith(fetch(req).then((res) => {
      const copy = res.clone();
      caches.open(VERSION).then((c) => c.put('/index.html', copy));
      return res;
    }).catch(() => caches.match('/index.html')));
    return;
  }

  if (url.pathname.startsWith('/assets/') || url.pathname.startsWith('/icons/')) {
    event.respondWith(caches.match(req).then((hit) => hit || fetch(req).then((res) => {
      if (res.ok) { const copy = res.clone(); caches.open(VERSION).then((c) => c.put(req, copy)); }
      return res;
    })));
  }
});
