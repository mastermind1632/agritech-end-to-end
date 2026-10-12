import { useCallback, useEffect, useMemo, useState } from 'react';

/**
 * Shopping cart for the Farm Market.
 * - Kept in localStorage per farmer, so it survives a refresh and is never mixed between accounts.
 * - Each line is a snapshot of one listing plus the quantity the buyer wants.
 * - Money is added up in cents so totals never show float errors like R0.30000000000000004.
 */
const keyFor = (farmerId) => `agritech_cart_${farmerId || 'guest'}`;

const read = (key) => {
  try {
    const list = JSON.parse(localStorage.getItem(key) || '[]');
    return Array.isArray(list) ? list : [];
  } catch { return []; }
};

const cents = (n) => Math.round(Number(n || 0) * 100);
export const lineTotal = (line) => Math.round(cents(line.price) * Number(line.quantity || 0)) / 100;

export default function useCart(farmerId) {
  const key = keyFor(farmerId);
  const [lines, setLines] = useState(() => read(key));

  useEffect(() => { setLines(read(key)); }, [key]);
  useEffect(() => {
    try { localStorage.setItem(key, JSON.stringify(lines)); } catch { /* storage full or blocked: cart still works this session */ }
  }, [key, lines]);

  const clampQty = (q, max) => {
    const n = Math.round(Number(q) * 100) / 100;
    if (!Number.isFinite(n) || n <= 0) return 0;
    return max != null ? Math.min(n, Number(max)) : n;
  };

  const add = useCallback((listing, qty = 1) => {
    setLines((cur) => {
      const snap = {
        id: listing.id,
        product: listing.product,
        grade: listing.grade || '',
        unit: listing.unit,
        sellerName: listing.sellerName,
        price: Number(listing.effectivePrice),
        maxQty: Number(listing.quantityAvailable),
        imageId: listing.imageIds && listing.imageIds.length ? listing.imageIds[0] : null
      };
      const existing = cur.find((l) => l.id === listing.id);
      if (existing) {
        return cur.map((l) => (l.id === listing.id
          ? { ...l, ...snap, quantity: clampQty(Number(l.quantity) + qty, snap.maxQty) || l.quantity }
          : l));
      }
      return [...cur, { ...snap, quantity: clampQty(qty, snap.maxQty) || 1 }];
    });
  }, []);

  const setQuantity = useCallback((id, qty) => {
    setLines((cur) => cur.map((l) => (l.id === id ? { ...l, quantity: qty } : l)));
  }, []);

  const remove = useCallback((id) => setLines((cur) => cur.filter((l) => l.id !== id)), []);
  const removeMany = useCallback((ids) => setLines((cur) => cur.filter((l) => !ids.includes(l.id))), []);
  const clear = useCallback(() => setLines([]), []);

  /** Refresh price and stock of cart lines from fresh listing data (e.g. after a search). */
  const sync = useCallback((listings) => {
    if (!listings || !listings.length) return;
    setLines((cur) => {
      let changed = false;
      const next = cur.map((l) => {
        const fresh = listings.find((x) => x.id === l.id);
        if (!fresh) return l;
        const price = Number(fresh.effectivePrice);
        const maxQty = Number(fresh.quantityAvailable);
        if (price === l.price && maxQty === l.maxQty) return l;
        changed = true;
        return { ...l, price, maxQty };
      });
      return changed ? next : cur;
    });
  }, []);

  const totals = useMemo(() => {
    const valid = lines.filter((l) => Number(l.quantity) > 0);
    const totalCents = valid.reduce((sum, l) => sum + Math.round(cents(l.price) * Number(l.quantity)), 0);
    return {
      itemCount: lines.length,                 // distinct products in the cart (what the badge shows)
      totalPrice: totalCents / 100             // everything in the cart, in rand
    };
  }, [lines]);

  return { lines, add, setQuantity, remove, removeMany, clear, sync, ...totals };
}
