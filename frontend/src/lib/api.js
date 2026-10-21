// Default is a same-origin "/api": Vite proxies it to Spring Boot in development and nginx does the
// same in Docker. (The old default of http://localhost:8080/api caused CORS failures in Docker.)
import { tr } from './i18n.js';

export const API = import.meta.env.VITE_API_URL || '/api';

export const TOKEN_KEY = 'agritech_token';
export const REFRESH_KEY = 'agritech_refresh';
export const FARMER_KEY = 'agritech_farmer';

// Plain-language message for when the server gives no specific reason.
export function friendlyStatus(status) {
  if (status === 400 || status === 422) return tr('Something in that form is not right. Please check it and try again.');
  if (status === 403) return tr('You are not allowed to do that.');
  if (status === 404) return tr('We could not find what you were looking for.');
  if (status === 409) return tr('That already exists.');
  if (status === 413) return tr('That file is too big. Please choose a smaller one.');
  if (status === 429) return tr('Too many tries. Please wait a moment and try again.');
  if (status >= 500) return tr('Something went wrong on our side. Please try again in a moment.');
  return tr('Something went wrong. Please try again.');
}

export function clearSession() {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(REFRESH_KEY);
  localStorage.removeItem(FARMER_KEY);
  // Drop cached offline answers so the next person on a shared phone never sees this user's data.
  try { navigator.serviceWorker?.controller?.postMessage('clear-api-cache'); } catch { /* no service worker */ }
}

export function saveSession(data) {
  if (data?.token) localStorage.setItem(TOKEN_KEY, data.token);
  if (data?.refreshToken) localStorage.setItem(REFRESH_KEY, data.refreshToken);
  if (data?.farmer) localStorage.setItem(FARMER_KEY, JSON.stringify(data.farmer));
}

// Access tokens are short-lived (30 min). When one expires, swap the refresh token for a new pair once
// (shared by all requests that fail at the same moment) and retry, so people are not thrown out mid-task.
let refreshing = null;
export function refreshSession() {
  const refreshToken = localStorage.getItem(REFRESH_KEY);
  if (!refreshToken) return Promise.resolve(false);
  if (!refreshing) {
    refreshing = fetch(`${API}/auth/refresh`, {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ refreshToken })
    }).then(async (r) => {
      if (!r.ok) return false;
      const data = await r.json();
      saveSession(data);
      window.dispatchEvent(new CustomEvent('agritech:session', { detail: data.farmer }));
      return true;
    }).catch(() => false).finally(() => { setTimeout(() => { refreshing = null; }, 0); });
  }
  return refreshing;
}

const isAuthPath = (path) => /^\/(farmers\/(login|register)|auth\/)/.test(path);

function sessionEnded() {
  clearSession();
  window.location.reload();
  return new Error(tr('Your session has ended. Please sign in again.'));
}

export async function api(path, options = {}, retried = false) {
  const token = localStorage.getItem(TOKEN_KEY);
  const isForm = typeof FormData !== 'undefined' && options.body instanceof FormData;
  let res;
  try {
    res = await fetch(`${API}${path}`, {
      ...options,
      headers: {
        ...(isForm ? {} : { 'Content-Type': 'application/json' }),
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...(options.headers || {})
      }
    });
  } catch {
    throw new Error(tr(navigator.onLine === false
      ? 'You are offline. This will work again when you have signal.'
      : 'We could not connect. Please check your internet connection and try again.'));
  }

  if (res.status === 401 && token && !isAuthPath(path)) {
    if (!retried && await refreshSession()) return api(path, options, true);
    throw sessionEnded();
  }

  const text = await res.text();
  let data = null;
  try { data = text ? JSON.parse(text) : null; } catch { data = text; }

  if (!res.ok) {
    const msg = (data && typeof data === 'object' && (data.message || data.detail || data.error)) || (typeof data === 'string' && data.length < 300 && data) || '';
    const error = new Error(msg ? tr(msg) : friendlyStatus(res.status));
    error.status = res.status;
    throw error;
  }
  return data;
}

const json = (body) => JSON.stringify(body ?? {});
const post = (path, body) => api(path, { method: 'POST', body: json(body) });
const put = (path, body) => api(path, { method: 'PUT', body: json(body) });
const del = (path) => api(path, { method: 'DELETE' });
const qs = (params = {}) => {
  const q = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => { if (v !== '' && v != null && v !== false) q.set(k, v); });
  const s = q.toString();
  return s ? `?${s}` : '';
};

/** Multipart upload with the login token (and one silent refresh on expiry). */
export async function upload(path, formData) {
  return api(path, { method: 'POST', body: formData });
}

/** Downloads a protected file (Excel, CSV, JSON, document) and hands it to the browser. */
export async function download(path, fallbackName = 'download') {
  const go = () => fetch(`${API}${path}`, { headers: { Authorization: `Bearer ${localStorage.getItem(TOKEN_KEY)}` } });
  let r = await go();
  if (r.status === 401 && await refreshSession()) r = await go();
  if (!r.ok) {
    let msg = friendlyStatus(r.status);
    try { msg = (await r.json()).message || msg; } catch { /* keep */ }
    throw new Error(msg);
  }
  const blob = await r.blob();
  const cd = r.headers.get('Content-Disposition') || '';
  const match = /filename\*?=(?:UTF-8'')?"?([^";]+)"?/i.exec(cd);
  const name = match ? decodeURIComponent(match[1]) : fallbackName;
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url; a.download = name; document.body.appendChild(a); a.click(); a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 2000);
}

/** Opens a protected file (e.g. a PDF from the vault) in a new tab. */
export async function openFile(path) {
  const r = await fetch(`${API}${path}`, { headers: { Authorization: `Bearer ${localStorage.getItem(TOKEN_KEY)}` } });
  if (!r.ok) throw new Error(friendlyStatus(r.status));
  const url = URL.createObjectURL(await r.blob());
  window.open(url, '_blank', 'noopener');
  setTimeout(() => URL.revokeObjectURL(url), 60000);
}

export const farmerApi = {
  login: (body) => post('/farmers/login', body),
  register: (body) => post('/farmers/register', body),
  get: (id) => api(`/farmers/${id}`),
  me: () => api('/farmers/me'),
  updateMe: (body) => put('/farmers/me', body)
};

export const authApi = {
  logout: () => post('/auth/logout', { refreshToken: localStorage.getItem(REFRESH_KEY) }),
  logoutAll: () => post('/auth/logout-all'),
  forgot: (identifier) => post('/auth/forgot-password', { identifier }),
  reset: (token, password) => post('/auth/reset-password', { token, password }),
  changePassword: (currentPassword, newPassword) => post('/auth/change-password', { currentPassword, newPassword }),
  mfaSetup: () => post('/auth/mfa/setup'),
  mfaEnable: (code) => post('/auth/mfa/enable', { code }),
  mfaDisable: (code) => post('/auth/mfa/disable', { code })
};

export const intelligenceApi = {
  options: () => api('/market-intelligence/options'),
  profile: () => api('/market-intelligence/profile'),
  saveProfile: body => api('/market-intelligence/profile', { method: 'PUT', body: JSON.stringify(body) }),
  deleteProfile: () => api('/market-intelligence/profile', { method: 'DELETE' }),
  recommendations: () => api('/market-intelligence/recommendations'),
  demand: () => api('/market-intelligence/demand'),
  records: query => api(`/market-intelligence/records?${new URLSearchParams(query)}`),
  addRecord: body => api('/market-intelligence/records', { method: 'POST', body: JSON.stringify(body) }),
  deleteRecord: id => api(`/market-intelligence/records/${encodeURIComponent(id)}`, { method: 'DELETE' }),
  feedback: body => api('/market-intelligence/feedback', { method: 'POST', body: JSON.stringify(body) })
};

export const financeApi = {
  summary: (id) => api(`/finance/${id}/summary`),
  expenses: (id) => api(`/finance/expenses/${id}`),
  income: (id) => api(`/finance/income/${id}`),
  monthly: (months = 12) => api(`/finance/monthly?months=${months}`),
  addExpense: (body) => post('/finance/expenses', body),
  addIncome: (body) => post('/finance/income', body),
  deleteExpense: (id, farmerId) => api(`/finance/expenses/${id}?farmerId=${encodeURIComponent(farmerId)}`, { method: 'DELETE' }),
  deleteIncome: (id, farmerId) => api(`/finance/income/${id}?farmerId=${encodeURIComponent(farmerId)}`, { method: 'DELETE' }),
  exportXlsx: () => download('/finance/export.xlsx', 'farm-finances.xlsx')
};

export const marketApi = {
  suppliers: () => api('/suppliers'),
  mine: () => api('/suppliers/mine'),
  products: (id) => api(`/suppliers/${id}/products`),
  createSupplier: (body) => post('/suppliers', body),
  updateSupplier: (id, body) => put(`/suppliers/${id}`, body),
  deleteSupplier: (id) => del(`/suppliers/${id}`),
  addProduct: (supplierId, body) => post(`/suppliers/${supplierId}/products`, body),
  updateProduct: (id, body) => put(`/suppliers/products/${id}`, body),
  deleteProduct: (id) => del(`/suppliers/products/${id}`)
};

export const groupApi = {
  create: (body) => post('/group-orders', body),
  list: (status = 'open') => api(`/group-orders?status=${encodeURIComponent(status)}`),
  items: (id) => api(`/group-orders/${id}/items`),
  join: (id, body) => post(`/group-orders/${id}/join`, body),
  close: (id) => put(`/group-orders/${id}/close`)
};

export const recommendationApi = {
  quote: (id, quantity) => api(`/recommendations/${encodeURIComponent(id)}/quote?quantity=${encodeURIComponent(quantity)}`),
  list: (id) => api(`/recommendations/farmer/${id}`),
  generate: (id) => post('/recommendations/generate', { farmerId: id })
};

export const assistantApi = {
  createConversation: () => api('/ai/conversations', { method: 'POST' }),
  conversation: id => api(`/ai/conversations/${encodeURIComponent(id)}`),
  deleteConversation: id => api(`/ai/conversations/${encodeURIComponent(id)}`, { method: 'DELETE' }),
  turn: (id, prompt, action) => api(`/ai/conversations/${encodeURIComponent(id)}/turn`,
    { method: 'POST', body: JSON.stringify({ prompt, action }) }),
  resume: (id, interruptId, approved) => api(`/ai/conversations/${encodeURIComponent(id)}/resume`,
    { method: 'POST', body: JSON.stringify({ interruptId, approved }) }),
  chat: (prompt) => post('/ai/chat', { prompt }),
  buyingGroup: () => api('/ai/farmer-clusters'),
  similar: (id) => api(`/ai/farmers/${encodeURIComponent(id)}/nearest`),
  alerts: (id) => api(`/ai/farmers/${encodeURIComponent(id)}/anomalies`)
};

export const chatApi = {
  farmers: () => api('/chat/farmers'),
  conversations: () => api('/chat/conversations'),
  history: (room) => api(`/chat/rooms/${encodeURIComponent(room)}/messages`),
  send: (room, body) => post(`/chat/rooms/${encodeURIComponent(room)}/messages`, { body })
};

const authHeader = () => {
  const t = localStorage.getItem(TOKEN_KEY);
  return t ? { Authorization: `Bearer ${t}` } : {};
};
const failMessage = async (r, fallback) => {
  try { const d = await r.json(); return d.message || fallback; } catch { return fallback; }
};
export const avatarApi = {
  fetch: async (id) => {
    const r = await fetch(`${API}/farmers/${encodeURIComponent(id)}/avatar`, { headers: authHeader() });
    return r.status === 200 ? r.blob() : null;
  },
  upload: async (id, file) => {
    const fd = new FormData();
    fd.append('file', file);
    const r = await fetch(`${API}/farmers/${encodeURIComponent(id)}/avatar`, { method: 'POST', headers: authHeader(), body: fd });
    if (!r.ok) throw new Error(tr(await failMessage(r, tr('Upload failed ({status})', { status: r.status }))));
  },
  remove: async (id) => {
    const r = await fetch(`${API}/farmers/${encodeURIComponent(id)}/avatar`, { method: 'DELETE', headers: authHeader() });
    if (!r.ok) throw new Error(tr(await failMessage(r, tr('Could not remove photo ({status})', { status: r.status }))));
  }
};

export const farmMarketApi = {
  search: (params = {}) => api(`/market/listings${qs(params)}`),
  mine: () => api('/market/listings/mine'),
  create: (body) => post('/market/listings', body),
  update: (id, body) => put(`/market/listings/${id}`, body),
  remove: (id) => del(`/market/listings/${id}`),
  prices: (limit = 12) => api(`/market/prices?limit=${limit}`)
};

export const disputeApi = {
  reasons: () => api('/market/disputes/reasons'),
  raise: (orderId, body) => post(`/market/orders/${orderId}/disputes`, body),
  mine: () => api('/market/disputes'),
  get: (id) => api(`/market/disputes/${id}`),
  respond: (id, response) => post(`/market/disputes/${id}/respond`, { response }),
  withdraw: (id) => post(`/market/disputes/${id}/withdraw`),
  evidence: (id, file, note) => {
    const fd = new FormData();
    fd.append('file', file);
    if (note) fd.append('note', note);
    return upload(`/market/disputes/${id}/evidence`, fd);
  }
};

export const notificationApi = {
  list: (page = 0, size = 30) => api(`/me/notifications?page=${page}&size=${size}`),
  unread: () => api('/me/notifications/unread-count'),
  read: (id) => put(`/me/notifications/${id}/read`),
  readAll: () => put('/me/notifications/read-all'),
  preferences: () => api('/me/notification-preferences'),
  savePreferences: (body) => put('/me/notification-preferences', body),
  ticket: () => post('/live/ticket')
};

export const insightsApi = {
  overview: () => api('/insights/overview')
};

export const companionApi = {
  profile: () => api('/companion/profile'),
  saveProfile: (body) => put('/companion/profile', body),
  checklist: () => api('/companion/checklist'),
  updateStep: (code, body) => put(`/companion/checklist/${encodeURIComponent(code)}`, body),
  summary: () => api('/companion/summary'),
  deadlines: () => api('/companion/deadlines'),
  addDeadline: (body) => post('/companion/deadlines', body),
  setDeadline: (id, status) => put(`/companion/deadlines/${id}`, { status }),
  deleteDeadline: (id) => del(`/companion/deadlines/${id}`),
  plan: (openingCash) => api(`/companion/plan${qs({ openingCash })}`),
  addCrop: (body) => post('/companion/plan/crops', body),
  updateCrop: (id, body) => put(`/companion/plan/crops/${id}`, body),
  deleteCrop: (id) => del(`/companion/plan/crops/${id}`),
  exportPlan: (openingCash) => download(`/companion/plan/export.xlsx${qs({ openingCash })}`, 'farm-business-plan.xlsx')
};

export const exportApi = {
  options: () => api('/public/export/options'),
  preview: (product, destination) => api(`/public/export/requirements${qs({ product, destination })}`),
  list: () => api('/export/workflows'),
  start: (body) => post('/export/workflows', body),
  get: (id) => api(`/export/workflows/${id}`),
  updateItem: (id, itemId, body) => put(`/export/workflows/${id}/items/${itemId}`, body),
  advance: (id) => post(`/export/workflows/${id}/advance`),
  close: (id, body) => post(`/export/workflows/${id}/close`, body)
};

export const statementApi = {
  categories: () => api('/statements/categories'),
  list: () => api('/statements'),
  get: (id) => api(`/statements/${id}`),
  upload: (file, consent, keepOriginal) => {
    const fd = new FormData();
    fd.append('file', file);
    fd.append('consent', String(Boolean(consent)));
    fd.append('keepOriginal', String(Boolean(keepOriginal)));
    return upload('/statements', fd);
  },
  updateTxn: (id, body) => put(`/statements/transactions/${id}`, body),
  confirm: (id) => post(`/statements/${id}/confirm`),
  reopen: (id) => post(`/statements/${id}/reopen`),
  remove: (id, keepLedger) => del(`/statements/${id}?keepLedger=${Boolean(keepLedger)}`),
  analysis: () => api('/statements/analysis'),
  rules: () => api('/statements/rules'),
  saveRule: (body) => post('/statements/rules', body),
  deleteRule: (id) => del(`/statements/rules/${id}`),
  exportXlsx: () => download('/statements/export.xlsx', 'farm-expense-analysis.xlsx'),
  exportCsv: () => download('/statements/export.csv', 'farm-transactions.csv')
};

export const vaultApi = {
  list: () => api('/vault'),
  types: () => api('/vault/types'),
  upload: (file, title, docType, expiresOn) => {
    const fd = new FormData();
    fd.append('file', file);
    if (title) fd.append('title', title);
    if (docType) fd.append('docType', docType);
    if (expiresOn) fd.append('expiresOn', expiresOn);
    return upload('/vault', fd);
  },
  update: (id, body) => put(`/vault/${id}`, body),
  remove: (id) => del(`/vault/${id}`),
  open: (id) => openFile(`/vault/${id}/download`),
  download: (id, name) => download(`/vault/${id}/download`, name)
};

export const privacyApi = {
  consents: () => api('/privacy/consents'),
  setConsent: (purpose, granted) => post('/privacy/consents', { purpose, granted }),
  exportData: () => download('/privacy/export', 'my-agritech-data.json'),
  requestDeletion: (reason) => post('/privacy/deletion-request', { reason }),
  requests: () => api('/privacy/requests')
};

export const adminApi = {
  stats: () => api('/admin/stats'),
  users: (params) => api(`/admin/users${qs(params)}`),
  setRole: (id, role) => put(`/admin/users/${id}/role`, { role }),
  setStatus: (id, status) => put(`/admin/users/${id}/status`, { status }),
  listings: (params) => api(`/admin/listings${qs(params)}`),
  removeListing: (id, reason) => post(`/admin/listings/${id}/remove`, { reason }),
  disputes: (openOnly = true) => api(`/admin/disputes?openOnly=${openOnly}`),
  resolve: (id, resolution, note) => post(`/admin/disputes/${id}/resolve`, { resolution, note }),
  audit: (params) => api(`/admin/audit${qs(params)}`),
  verifications: () => api('/admin/verifications'),
  verify: (farmerId, approve, note) => post(`/admin/verifications/${farmerId}`, { approve, note }),
  steps: () => api('/admin/companion/steps'),
  editStep: (code, body) => put(`/admin/companion/steps/${code}`, body),
  verifyStep: (code) => post(`/admin/companion/steps/${code}/verify`),
  requirements: () => api('/admin/export/requirements'),
  editRequirement: (id, body) => put(`/admin/export/requirements/${id}`, body),
  verifyRequirement: (id) => post(`/admin/export/requirements/${id}/verify`),
  changes: () => api('/admin/export/changes'),
  proposeChange: (body) => post('/admin/export/changes', body),
  review: (id, approve) => post(`/admin/export/changes/${id}/review`, { approve }),
  runMonitor: () => post('/admin/export/monitor/run'),
  dataRequests: (openOnly = true) => api(`/admin/data-requests?openOnly=${openOnly}`),
  completeDeletion: (id) => post(`/admin/data-requests/${id}/complete`)
};
