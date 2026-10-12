"""Plain-English replies for authenticated ledger and recommendation results."""
from decimal import Decimal
import re


SERVICE_NAMES = {'business_companion': 'Business registration support',
                 'export_readiness': 'Export readiness', 'expense_insights': 'Expense insights'}


def ledger_reply(question, ledger):
    if not ledger or not re.search(r'\b(my|recorded|how much)\b', question.lower()):
        return None
    income, expenses, profit = [Decimal(str(ledger[k])) for k in ('income', 'expenses', 'profit')]
    return (f'Your recorded income is R{income:.2f}, expenses are R{expenses:.2f}, and '
            f'income minus expenses is R{profit:.2f}. These totals cover your recorded entries only, '
            'not your bank balance. Open My Finances to review or update the entries.')


def produce_reply(result):
    if result.get('profileMissing'):
        return ('Complete your Production profile first, then open Produce Markets for buyer matches. '
                'Matching uses your crop, harvest month, delivery radius and seasonal capacity.')
    rows = result['recommendations']
    lines = ['Your farm group: ' + result['segment']['name'] + '.']
    if rows:
        lines.append('These recorded buyer requests match your production profile:')
        for row in rows[:3]:
            lines.append(f"- {row['displayName']}: {row['quantityKg']:g} kg of {row['crop']}, "
                         f"{row['district']}, {row['province']}; {row['availableFrom']} to {row['availableUntil']}.")
        lines.append('Open Produce Markets to review contacts, prices and delivery. Requests are not confirmed orders.')
    else:
        lines.append('No published buyer requests currently match your crop, harvest month, delivery radius and capacity. '
                     'Produce Markets shows the recorded requests; new matching requests can appear there later.')
    for service in result.get('services', []):
        lines.append(SERVICE_NAMES.get(service['service'], service['service'].replace('_', ' ')) + ': ' + service['reason'] + '.')
    return '\n'.join(lines)


def buying_reply(rows):
    if not rows:
        return 'No available group-buying opportunities are recorded right now. Open Group Orders to check current orders.'
    lines = ['These open group orders are available to explore:']
    for row in rows[:3]:
        price = Decimal(str(row['quote']['listedUnitPrice']))
        basis = 'Purchase match' if row['matchedItems'] else 'Open order, not a purchase match'
        lines.append(f"- {row['productName']}: R{price:.2f} per listed unit; {row['remainingQuantity']} units remaining. {basis}.")
    lines.append('Open AI Insights to compare listed prices and request an order review. Stock, delivery and listed discounts remain unconfirmed.')
    return '\n'.join(lines)
