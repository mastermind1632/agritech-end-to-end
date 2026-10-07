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
  if (reason === 'same location') return 'Same recorded area';
  if (reason === 'similar group-order activity') return 'Similar group-buying activity';
  if (reason === 'similar spending and activity pattern') return 'Similar recorded spending and buying activity';
  if (reason.startsWith('shared products or expense terms: ')) {
    return reason.replace('shared products or expense terms: ', 'Recorded items in common: ');
  }
  return 'Similar recorded activity';
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
    title: titles[alert.type] || 'Recorded activity to review',
    comparison: `Your recorded ${isMoney ? 'total' : 'count'}: ${format(alert.value)}. Average across farmers: ${format(alert.baseline)}.`,
    action: alert.type === 'low_data_signal'
      ? 'Your comparison may be less reliable with so few expense records.'
      : 'Check your entries and orders. A higher total does not necessarily mean a mistake.',
  };
}
