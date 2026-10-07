import { useState } from 'react';
import useLive from './useLive';
import { groupApi, marketApi, recommendationApi } from '../lib/api';

// Builds a farmer-facing activity feed from data the backend already exposes.
export default function useFarmerNotices(farmerId) {
  const key = 'agritech_notices_seen_' + farmerId;
  const [seen, setSeen] = useState(() => {
    try { return JSON.parse(localStorage.getItem(key) || '[]'); } catch { return []; }
  });

  const { data, error, loading } = useLive(async () => {
    const [open, done, recs, suppliers] = await Promise.all([
      groupApi.list('open'), groupApi.list('completed'),
      recommendationApi.list(farmerId), marketApi.suppliers()
    ]);
    const products = (await Promise.all((suppliers || []).map((s) => marketApi.products(s.id)))).flat();
    const name = (id) => products.find((p) => p.id === id)?.productName || 'a product';
    const orders = [...(open || []), ...(done || [])]
      .sort((a, b) => String(b.createdAt).localeCompare(String(a.createdAt)))
      .slice(0, 20);
    const mine = await Promise.all(orders.map((o) =>
      groupApi.items(o.id).then((items) => (items || []).find((i) => i.farmerId === farmerId)).catch(() => null)));

    const events = [];
    orders.forEach((o, i) => {
      const m = mine[i];
      const pct = Math.round((o.currentQuantity / o.targetQuantity) * 100);
      if (m) events.push({ id: 'join-' + o.id, type: 'joined', mark: '+', time: m.joinedAt,
        title: 'You joined the ' + name(o.productId) + ' group order',
        text: m.quantity + ' units committed, total R ' + Number(m.totalPrice).toFixed(2) + '.' });
      if (o.status === 'completed') events.push({ id: 'done-' + o.id, type: 'completed', mark: '!', time: o.createdAt,
        title: 'Group order reached its target: ' + name(o.productId),
        text: m ? 'You are part of this order. The supplier has been notified.' : 'This order is full and has been sent to the supplier.' });
      else events.push({ id: 'open-' + o.id, type: 'order', mark: '*', time: o.createdAt,
        title: 'Open group order: ' + name(o.productId),
        text: Number(o.discountRate) + '% discount, ' + pct + '% filled (' + o.currentQuantity + ' of ' + o.targetQuantity + ' units).' });
    });
    (recs || []).forEach((r) => events.push({ id: 'rec-' + r.id, type: 'insight', mark: 'i', time: r.createdAt,
      title: 'AI buying insight', text: r.reason }));
    events.sort((a, b) => String(b.time).localeCompare(String(a.time)));
    return events;
  }, [farmerId], { interval: 20000 });

  const events = data || [];
  const unread = events.filter((e) => !seen.includes(e.id)).length;
  const markAllRead = () => {
    const ids = events.map((e) => e.id).slice(0, 300);
    setSeen(ids);
    try { localStorage.setItem(key, JSON.stringify(ids)); } catch { /* ignore */ }
  };
  return { events, unread, error, loading, seen, markAllRead };
}