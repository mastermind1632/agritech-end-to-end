import { useState } from 'react';
import { compactMoney, money, monthLabel, niceMax } from '../lib/format';
import useLang from '../hooks/useLang';

/**
 * Grouped bars per month (e.g. income vs expenses). Pure SVG: no chart library, scales to any width,
 * and every bar has a tooltip with the exact value.
 */
export function BarChart({ rows, series, height = 220, format = compactMoney, labelOf = (r) => monthLabel(r.month) }) {
  const [hover, setHover] = useState(null);
  const { t } = useLang();
  if (!rows?.length) return null;
  const max = niceMax(Math.max(...rows.flatMap((r) => series.map((s) => Math.abs(Number(r[s.key]) || 0)))));
  const W = 640, H = height, padL = 52, padB = 26, padT = 10;
  const plotW = W - padL - 8, plotH = H - padB - padT;
  const group = plotW / rows.length;
  const barW = Math.max(4, Math.min(22, (group - 8) / series.length));
  const ticks = [0, 0.25, 0.5, 0.75, 1];
  return (
    <div className="chart">
      <svg viewBox={`0 0 ${W} ${H}`} role="img" aria-label={t('{series} by month', { series: series.map((s) => t(s.label)).join(' / ') })}>
        {ticks.map((t) => {
          const y = padT + plotH - t * plotH;
          return (
            <g key={t}>
              <line x1={padL} x2={W - 8} y1={y} y2={y} className="chart-grid" />
              <text x={padL - 6} y={y + 4} textAnchor="end" className="chart-axis">{format(max * t)}</text>
            </g>
          );
        })}
        {rows.map((r, i) => {
          const x0 = padL + i * group + (group - barW * series.length) / 2;
          return (
            <g key={i} onMouseLeave={() => setHover(null)}>
              {series.map((s, j) => {
                const v = Math.abs(Number(r[s.key]) || 0);
                const h = (v / max) * plotH;
                return (
                  <rect key={s.key} className="bar" style={{ animationDelay: `${i * 60 + j * 30}ms` }} x={x0 + j * barW} y={padT + plotH - h} width={barW - 2} height={Math.max(h, v > 0 ? 1 : 0)}
                        rx="2" fill={s.color} onMouseEnter={() => setHover({ i, s, v })}>
                    <title>{`${labelOf(r)} - ${t(s.label)}: ${money(v)}`}</title>
                  </rect>
                );
              })}
              <text x={padL + i * group + group / 2} y={H - 8} textAnchor="middle" className="chart-axis">{labelOf(r)}</text>
            </g>
          );
        })}
      </svg>
      <div className="chart-legend">
        {series.map((s) => <span key={s.key}><i style={{ background: s.color }} />{t(s.label)}</span>)}
        {hover && <span className="chart-hover">{labelOf(rows[hover.i])}: {t(hover.s.label)} {money(hover.v)}</span>}
      </div>
    </div>
  );
}

/** Horizontal bars for a ranked list (spending by category, cost per crop). */
export function BarList({ items, valueKey = 'amount', labelKey = 'label', format = money, color = 'var(--green)', note }) {
  const { t } = useLang();
  if (!items?.length) return null;
  const max = Math.max(...items.map((i) => Number(i[valueKey]) || 0)) || 1;
  return (
    <div className="barlist">
      {items.map((it, idx) => {
        const v = Number(it[valueKey]) || 0;
        return (
          <div className="barlist-row" key={idx}>
            <div className="barlist-head"><span>{t(it[labelKey])}</span><b>{format(v)}</b></div>
            <div className="barlist-track"><span style={{ width: `${Math.max(2, (v / max) * 100)}%`, background: it.color || color, animationDelay: `${idx * 70}ms` }} /></div>
            {note && note(it) && <small className="muted">{note(it)}</small>}
          </div>
        );
      })}
    </div>
  );
}

/** Circular progress (checklist completion, export readiness). */
export function ProgressRing({ value = 0, size = 76, stroke = 8, label, color = 'var(--green)' }) {
  const r = (size - stroke) / 2;
  const c = 2 * Math.PI * r;
  const pct = Math.max(0, Math.min(100, Number(value) || 0));
  return (
    <div className="ring" style={{ width: size, height: size }}>
      <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} aria-label={`${pct}% ${label || ''}`}>
        <circle cx={size / 2} cy={size / 2} r={r} fill="none" className="ring-track" strokeWidth={stroke} />
        <circle cx={size / 2} cy={size / 2} r={r} fill="none" stroke={color} strokeWidth={stroke} strokeLinecap="round"
                strokeDasharray={`${(pct / 100) * c} ${c}`} transform={`rotate(-90 ${size / 2} ${size / 2})`} />
      </svg>
      <span className="ring-value">{pct}%</span>
    </div>
  );
}

/** Thin progress bar with a label. */
export function Progress({ value, label, tone }) {
  const { t } = useLang();
  const pct = Math.max(0, Math.min(100, Number(value) || 0));
  return (
    <div className="progress-labelled">
      {label && <div className="progress-head"><span>{t(label)}</span><b>{pct}%</b></div>}
      <div className={`progress ${tone || ''}`}><span style={{ width: `${pct}%` }} /></div>
    </div>
  );
}
