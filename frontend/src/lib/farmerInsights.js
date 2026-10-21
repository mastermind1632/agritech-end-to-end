import { tr } from './i18n.js';

export function ownBuyingGroup(data, farmerId) {
  if (farmerId == null) return null;
  const group = data?.clusters?.find((group) => group.farmers?.some(
    (member) => String(member.farmer.farmerId) === String(farmerId)));
  if (!group) return null;
  return { others: Math.max(0, group.farmers.length - 1), farmer: group.farmers.find(
    (member) => String(member.farmer.farmerId) === String(farmerId)).farmer };
}

export function hasRecordedActivity(farmer) {
  return Number(farmer?.expenseCount || 0) > 0 || Number(farmer?.orderCount || 0) > 0;
}

export function matchReason(reason) {
  if (reason === 'same location') return tr('Same recorded area');
  if (reason === 'similar group-order activity') return tr('Similar group-buying activity');
  if (reason === 'similar recorded expense totals') return tr('Similar recorded expense totals');
  if (reason === 'similar spending and activity pattern') return tr('Similar recorded spending and buying activity');
  if (reason.startsWith('shared products or expense terms: ')) {
    return tr('Recorded items in common: {items}', { items: reason.replace('shared products or expense terms: ', '') });
  }
  return tr('Similar recorded activity');
}

export function summarizeMatches(matches = []) {
  const summaries = new Map();
  for (const match of matches) {
    for (const label of new Set((match.reasons || []).map(matchReason))) {
      summaries.set(label, (summaries.get(label) || 0) + 1);
    }
  }
  return [...summaries].map(([label, count]) => ({ label, count }));
}

const money = (value) => new Intl.NumberFormat('en-ZA', {
  style: 'currency', currency: 'ZAR', maximumFractionDigits: 2,
}).format(Number(value));

export function spendingAlert(alert) {
  const titles = {
    expense_spike: 'Higher recorded expenses',
    order_spend_spike: 'Higher group-buying costs',
    order_activity_spike: 'More recorded group orders',
    low_data_signal: 'Few expense records',
  };
  const isMoney = ['totalExpense', 'orderSpend'].includes(alert.metric);
  const format = isMoney ? money : (value) => new Intl.NumberFormat('en-ZA', { maximumFractionDigits: 1 }).format(Number(value));
  return {
    title: tr(titles[alert.type] || 'Recorded activity to review'),
    comparison: tr(isMoney ? 'Your recorded total: {value}. Average across farmers: {baseline}.' : 'Your recorded count: {value}. Average across farmers: {baseline}.',
      { value: format(alert.value), baseline: format(alert.baseline) }),
    action: alert.type === 'low_data_signal'
      ? tr('Your comparison may be less reliable with so few expense records.')
      : tr('Check your entries and orders. A higher total does not necessarily mean a mistake.'),
  };
}
