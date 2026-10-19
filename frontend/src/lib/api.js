// Default is a same-origin "/api": Vite proxies it to Spring Boot in development and nginx does the
// same in Docker. (The old default of http://localhost:8080/api caused CORS failures in Docker.)
const API = import.meta.env.VITE_API_URL || '/api';

export const TOKEN_KEY = 'agritech_token';
export const FARMER_KEY = 'agritech_farmer';

function friendlyStatus(status) {
  if (status === 400 || status === 422) return 'Something in that form is not right. Please check it and try again.';
  if (status === 403) return 'You are not allowed to do that.';
  if (status === 404) return 'We could not find what you were looking for.';
  if (status === 409) return 'That already exists.';
  if (status === 413) return 'That file is too big. Please choose a smaller one.';
  if (status === 429) return 'Too many tries. Please wait a moment and try again.';
  if (status >= 500) return 'Something went wrong on our side. Please try again in a moment.';
  return 'Something went wrong. Please try again.';
}

export async function api(path, options = {}) {
  const token = localStorage.getItem(TOKEN_KEY);
  const isFormData = typeof FormData !== 'undefined' && options.body instanceof FormData;
  const headers = {
    ...(!isFormData ? { 'Content-Type': 'application/json' } : {}),
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
    ...(options.headers || {})
  };
  if (isFormData) delete headers['Content-Type'];
  let res;
  try {
    res = await fetch(`${API}${path}`, {
      ...options,
      headers
    });
  } catch {
    throw new Error('We could not connect. Please check your internet connection and try again.');
  }

  const text = await res.text();
  let data = null;
  try { data = text ? JSON.parse(text) : null } catch { data = text }

  if (res.status === 401 && token && !path.startsWith('/farmers/login') && !path.startsWith('/farmers/register')) {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(FARMER_KEY);
    window.location.reload();
    throw new Error('Your session has expired. Please sign in again.');
  }

  if (!res.ok) {
    const msg = (data && typeof data === 'object' && (data.message || data.detail || data.error)) || (typeof data === 'string' && data) || '';
    const error = new Error(msg || friendlyStatus(res.status));
    error.status = res.status;
    throw error;
  }
  return data;
}

export const farmerApi = {
  login: (body) => api('/farmers/login', { method: 'POST', body: JSON.stringify(body) }),
  register: (body) => api('/farmers/register', { method: 'POST', body: JSON.stringify(body) }),
  get: (id) => api(`/farmers/${id}`)
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
  addExpense: (body) => api('/finance/expenses', { method: 'POST', body: JSON.stringify(body) }),
  addIncome: (body) => api('/finance/income', { method: 'POST', body: JSON.stringify(body) }),
  deleteExpense: (id, farmerId) => api(`/finance/expenses/${id}?farmerId=${encodeURIComponent(farmerId)}`, { method: 'DELETE' }),
  deleteIncome: (id, farmerId) => api(`/finance/income/${id}?farmerId=${encodeURIComponent(farmerId)}`, { method: 'DELETE' })
};
export const marketApi = {
  suppliers: () => api('/suppliers'),
  products: (id) => api(`/suppliers/${id}/products`),
  createSupplier: (body) => api('/suppliers', { method: 'POST', body: JSON.stringify(body) }),
  updateSupplier: (id, body) => api(`/suppliers/${id}`, { method: 'PUT', body: JSON.stringify(body) }),
  deleteSupplier: (id) => api(`/suppliers/${id}`, { method: 'DELETE' }),
  addProduct: (supplierId, body) => api(`/suppliers/${supplierId}/products`, { method: 'POST', body: JSON.stringify(body) }),
  updateProduct: (id, body) => api(`/suppliers/products/${id}`, { method: 'PUT', body: JSON.stringify(body) }),
  deleteProduct: (id) => api(`/suppliers/products/${id}`, { method: 'DELETE' })
};
export const groupApi = {
  create: (body) => api('/group-orders', { method: 'POST', body: JSON.stringify(body) }),
  list: (status = 'open') => api(`/group-orders?status=${encodeURIComponent(status)}`),
  items: (id) => api(`/group-orders/${id}/items`),
  join: (id, body) => api(`/group-orders/${id}/join`, { method: 'POST', body: JSON.stringify(body) })
};
export const recommendationApi = {
  quote: (id, quantity) => api(`/recommendations/${encodeURIComponent(id)}/quote?quantity=${encodeURIComponent(quantity)}`),
  list: (id) => api(`/recommendations/farmer/${id}`),
  generate: (id) => api('/recommendations/generate', { method: 'POST', body: JSON.stringify({ farmerId: id }) })
};

export const assistantApi = {
  createConversation: () => api('/ai/conversations', { method: 'POST' }),
  conversation: id => api(`/ai/conversations/${encodeURIComponent(id)}`),
  deleteConversation: id => api(`/ai/conversations/${encodeURIComponent(id)}`, { method: 'DELETE' }),
  turn: (id, prompt, action) => api(`/ai/conversations/${encodeURIComponent(id)}/turn`,
    { method: 'POST', body: JSON.stringify({ prompt, action }) }),
  resume: (id, interruptId, approved) => api(`/ai/conversations/${encodeURIComponent(id)}/resume`,
    { method: 'POST', body: JSON.stringify({ interruptId, approved }) }),
  chat: (prompt) => api('/ai/chat', { method: 'POST', body: JSON.stringify({ prompt }) }),
  buyingGroup: () => api('/ai/farmer-clusters'),
  similar: (id) => api(`/ai/farmers/${encodeURIComponent(id)}/nearest`),
  alerts: (id) => api(`/ai/farmers/${encodeURIComponent(id)}/anomalies`)
};

export const chatApi = {
  farmers: () => api('/chat/farmers'),
  conversations: () => api('/chat/conversations'),
  history: (room) => api(`/chat/rooms/${encodeURIComponent(room)}/messages`),
  send: (room, body) => api(`/chat/rooms/${encodeURIComponent(room)}/messages`, { method: 'POST', body: JSON.stringify({ body }) })
};

export const exportApi = {
  applications: () => api('/exports/applications'),
  application: id => api(`/exports/applications/${encodeURIComponent(id)}`),
  createApplication: body => api('/exports/applications', { method: 'POST', body: JSON.stringify(body) }),
  updateApplication: (id, body) => api(`/exports/applications/${encodeURIComponent(id)}`,
    { method: 'PUT', body: JSON.stringify(body) }),
  updateTask: (id, taskKey, body) => api(`/exports/applications/${encodeURIComponent(id)}/tasks/${encodeURIComponent(taskKey)}`,
    { method: 'PUT', body: JSON.stringify(body) }),
  requirements: (product, destination) => api(`/exports/requirements?${new URLSearchParams({ product, destination })}`),
  uploadDocument: (id, formData) => api(`/exports/applications/${encodeURIComponent(id)}/documents`,
    { method: 'POST', body: formData }),
  updateDocument: (id, body) => api(`/exports/documents/${encodeURIComponent(id)}`,
    { method: 'PUT', body: JSON.stringify(body) }),
  deleteDocument: id => api(`/exports/documents/${encodeURIComponent(id)}`, { method: 'DELETE' }),
  downloadDocument: async id => {
    const token = localStorage.getItem(TOKEN_KEY);
    const response = await fetch(`${API}/exports/documents/${encodeURIComponent(id)}/download`, {
      headers: token ? { Authorization: `Bearer ${token}` } : {}
    });
    if (!response.ok) {
      const text = await response.text();
      let message = 'We could not download that document.';
      try {
        const data = text ? JSON.parse(text) : null;
        if (data?.message) message = data.message;
      } catch { /* Keep the plain-language fallback for non-JSON errors. */ }
      throw new Error(message);
    }
    return response.blob();
  },
  createConsignment: (id, body) => api(`/exports/applications/${encodeURIComponent(id)}/consignments`,
    { method: 'POST', body: JSON.stringify(body) }),
  updateConsignment: (applicationId, consignmentId, body) => api(
    `/exports/applications/${encodeURIComponent(applicationId)}/consignments/${encodeURIComponent(consignmentId)}`,
    { method: 'PUT', body: JSON.stringify(body) }),
  close: (id, body) => api(`/exports/applications/${encodeURIComponent(id)}/close`,
    { method: 'PUT', body: JSON.stringify(body) }),
  getShipments: (farmerId) => api(`/export/${farmerId}`),
  createShipment: (farmerId, data) => api(`/export/${farmerId}`, { method: 'POST', body: JSON.stringify(data) })
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
    if (!r.ok) throw new Error(await failMessage(r, `Upload failed (${r.status})`));
  },
  remove: async (id) => {
    const r = await fetch(`${API}/farmers/${encodeURIComponent(id)}/avatar`, { method: 'DELETE', headers: authHeader() });
    if (!r.ok) throw new Error(await failMessage(r, `Could not remove photo (${r.status})`));
  }
};

export const farmMarketApi = {
  search: (params = {}) => {
    const qs = new URLSearchParams();
    Object.entries(params).forEach(([k, v]) => { if (v !== '' && v != null) qs.set(k, v); });
    const s = qs.toString();
    return api(`/market/listings${s ? `?${s}` : ''}`);
  },
  mine: () => api('/market/listings/mine'),
  create: (body) => api('/market/listings', { method: 'POST', body: JSON.stringify(body) }),
  update: (id, body) => api(`/market/listings/${id}`, { method: 'PUT', body: JSON.stringify(body) }),
  remove: (id) => api(`/market/listings/${id}`, { method: 'DELETE' })
};

// --- Compliance (?4) ---
export const complianceApi = {
  getChecklist: (farmerId) => api(`/compliance/${farmerId}`),
  updateCompliance: (farmerId, data) => api(`/compliance/${farmerId}`, { method: 'POST', body: JSON.stringify(data) }),
  getReminders: (farmerId) => api(`/compliance/${farmerId}/reminders`),
  regenerateReminders: (farmerId) => api(`/compliance/${farmerId}/reminders/regenerate`, { method: 'POST' }),
  sendReminder: (farmerId, reminderId) => api(`/compliance/${farmerId}/reminders/${reminderId}/send-now`, { method: 'POST' })
};

// --- Statement Analysis (?6) ---
export const statementApi = {
  analyze: (payload) => api('/finance/statement/analyze', { method: 'POST', body: JSON.stringify(payload) }),
  uploadAndAnalyze: async (file) => {
    const token = localStorage.getItem(TOKEN_KEY);
    const fd = new FormData();
    fd.append('file', file);
    const res = await fetch(`${API}/finance/statement/upload`, {
      method: 'POST',
      headers: token ? { Authorization: `Bearer ${token}` } : {},
      body: fd
    });
    const data = await res.json().catch(() => ({}));
    if (!res.ok) throw new Error(data.message || data.detail || 'Upload failed');
    return data;
  }
};


