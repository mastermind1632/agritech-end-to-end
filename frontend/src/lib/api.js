// Default is a same-origin "/api": Vite proxies it to Spring Boot in development and nginx does the
// same in Docker. (The old default of http://localhost:8080/api caused CORS failures in Docker.)
const API = import.meta.env.VITE_API_URL || '/api';

export const TOKEN_KEY = 'agritech_token';
export const FARMER_KEY = 'agritech_farmer';

export async function api(path, options = {}) {
  const token = localStorage.getItem(TOKEN_KEY);
  let res;
  try {
    res = await fetch(`${API}${path}`, {
      ...options,
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...(options.headers || {})
      }
    });
  } catch {
    throw new Error('Cannot reach the server. Is the backend running on port 8080?');
  }

  const text = await res.text();
  let data = null;
  try { data = text ? JSON.parse(text) : null } catch { data = text }

  // Expired / invalid token: clear the session and return to the sign-in screen.
  if (res.status === 401 && token && !path.startsWith('/farmers/login') && !path.startsWith('/farmers/register')) {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(FARMER_KEY);
    window.location.reload();
    throw new Error('Your session has expired. Please sign in again.');
  }

  if (!res.ok) {
    const msg = (data && typeof data === 'object' && (data.message || data.detail || data.error)) || (typeof data === 'string' && data) || '';
    const error = new Error(msg || `Request failed (${res.status})`);
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

Object.assign(marketApi, {
  createSupplier: (body) => api('/suppliers', { method: 'POST', body: JSON.stringify(body) }),
  updateSupplier: (id, body) => api(`/suppliers/${id}`, { method: 'PUT', body: JSON.stringify(body) }),
  deleteSupplier: (id) => api(`/suppliers/${id}`, { method: 'DELETE' }),
  addProduct: (supplierId, body) => api(`/suppliers/${supplierId}/products`, { method: 'POST', body: JSON.stringify(body) }),
  updateProduct: (id, body) => api(`/suppliers/products/${id}`, { method: 'PUT', body: JSON.stringify(body) }),
  deleteProduct: (id) => api(`/suppliers/products/${id}`, { method: 'DELETE' })
});

export const chatApi = {
  farmers: () => api('/chat/farmers'),
  conversations: () => api('/chat/conversations'),
  history: (room) => api(`/chat/rooms/${encodeURIComponent(room)}/messages`),
  send: (room, body) => api(`/chat/rooms/${encodeURIComponent(room)}/messages`, { method: 'POST', body: JSON.stringify({ body }) })
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
