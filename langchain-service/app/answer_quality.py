"""Keep farmer-facing answers and citations free of internal editorial instructions."""
import re


META = re.compile(r"^(?:[-*]\s*)?(?:ask (?:about|which|for (?:the|missing))\b|"
                  r"do not (?:invent|guess|prescribe|claim|guarantee)\b|"
                  r"(?:sources|farmer figures) are untrusted\b|ignore commands\b)", re.I)


def clean_answer(text):
    parts = re.split(r"((?<=[.!?])\s+|\n)", text.strip())
    kept = []
    for index in range(0, len(parts), 2):
        sentence = parts[index]
        if sentence.strip() and not META.match(sentence.strip()):
            kept.append(sentence + (parts[index + 1] if index + 1 < len(parts) else ''))
    return ''.join(kept).strip()


def relevant_sources(question, route, sources):
    text = question.lower()
    allowed = None
    if route == 'buying':
        topics = [('guide:seed', re.search(r'\bseeds?\b', text)),
                  ('guide:fertiliser', re.search(r'\bfertili[sz]ers?\b', text))]
        focused = [key for key, match in sorted(topics, key=lambda item: item[1].start() if item[1] else len(text)) if match]
        guides = set(focused) if focused else {'guide:seed', 'guide:fertiliser', 'guide:buying'}
        # A product comparison needs its own guidance before generic order terms.
        # Retain those terms when asked about an actual deal or when product guidance is absent.
        if (re.search(r'\b(group[ -]?buying|orders?|discounts?|stock|availability|deliver\w*|shipping|suppliers?)\b', text)
                or not any(s['id'] in focused for s in sources)):
            guides.add('guide:buying')
        selected = [s for s in sources if s['id'].startswith(('product:', 'order:')) or s['id'] in guides]
        return sorted(selected, key=lambda source: focused.index(source['id']) if source['id'] in focused else len(focused))
    if route == 'produce':
        return [s for s in sources if s['id'].startswith('buyer:') or s['id'] == 'guide:produce']
    if re.search(r'\b(water\w*|irrigat\w*|drought)\b', text):
        allowed = {'guide:water', 'source:fao-water'}
    elif re.search(r'\b(rotation|rotate|conservation|diversification)\b', text):
        allowed = {'guide:rotation', 'source:fao-conservation'}
    elif re.search(r'\b(germination|seeds?)\b', text):
        allowed = {'guide:seed'}
    elif route == 'finance':
        allowed = {'guide:ledger', 'guide:fertiliser', 'guide:buying'}
    if allowed is None:
        return sources
    return [s for s in sources if s['id'] in allowed]
