import { tr } from './i18n.js';
import { dateTime } from './format.js';

// Printable reports (business plan, lender report, export checklist). Opens a clean page and the browser's
// print dialog, where "Save as PDF" produces the PDF. No PDF library needed, and it works on phones.
const esc = (s) => String(s ?? '').replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));

export function table(headers, rows) {
  return `<table><thead><tr>${headers.map((h) => `<th>${esc(h)}</th>`).join('')}</tr></thead><tbody>${
    rows.map((r) => `<tr>${r.map((c) => `<td>${esc(c)}</td>`).join('')}</tr>`).join('')}</tbody></table>`;
}

export function printReport(title, sections, subtitle = '') {
  const w = window.open('', '_blank');
  if (!w) throw new Error(tr('Allow pop-ups for this site to print or save the report as PDF.'));
  const body = sections.map((s) => `<section><h2>${esc(s.title)}</h2>${s.html || ''}${s.text ? `<p>${esc(s.text)}</p>` : ''}</section>`).join('');
  w.document.write(`<!doctype html><html><head><meta charset="utf-8"><title>${esc(title)}</title><style>
    body{font-family:Arial,Helvetica,sans-serif;color:#16241f;margin:32px;font-size:12px}
    h1{font-size:22px;margin:0 0 4px;color:#0d5c45} .sub{color:#62716b;margin:0 0 20px}
    h2{font-size:15px;margin:22px 0 8px;color:#0d5c45;border-bottom:2px solid #e4e9e6;padding-bottom:4px}
    table{width:100%;border-collapse:collapse;margin:6px 0}th,td{text-align:left;padding:6px 8px;border-bottom:1px solid #e4e9e6;vertical-align:top}
    th{background:#f2f6f4;font-size:11px;text-transform:uppercase;letter-spacing:.4px} .foot{margin-top:28px;color:#62716b;font-size:10px}
    @media print{body{margin:12mm}}</style></head><body>
    <h1>${esc(title)}</h1><p class="sub">${esc(subtitle)} · ${esc(tr('Generated {when} by AgriTech', { when: dateTime(new Date()) }))}</p>
    ${body}<p class="foot">${esc(tr('Generated from records entered and uploaded by the farmer. Guidance only - not tax, legal or financial advice.'))}</p>
    <script>window.onload=function(){setTimeout(function(){window.print()},300)}</script></body></html>`);
  w.document.close();
}
