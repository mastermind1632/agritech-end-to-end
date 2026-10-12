"""Resolve conversational intent and present verified catalogue facts without inventing suppliers."""
from decimal import Decimal, InvalidOperation
import json
import re


def normalise(question):
    return re.sub(r"\bseeda\b", "seeds", question.lower()).strip().rstrip("?.!")


def followup(question, messages=()):
    text = normalise(question)
    explicit = bool(re.search(r"\b(it|its|that|them|those)\b", text)
                or re.match(r"^(what about|and |which location|what location|which crop|what crop|why|more\b)", text)
                or re.fullmatch(r"(in )?(maize|tomato(es)?|beans|cabbage|limpopo|gauteng|polokwane)", text))
    if explicit:
        return True
    last = messages[-1]["text"].lower() if messages and messages[-1]["role"] == "assistant" else ""
    return bool(last.endswith('?') and re.search(r"which (crop|town)|your farm near", last)
                and len(text.split()) <= 6 and '?' not in question
                and not re.search(r"\b(how|what|where|why|when|water\w*|irrigation|soil|weather|expenses?|income|profit|fertilis\w*|fertiliz\w*|seeds?)\b", text))


def route_for(question, previous_route=None, messages=()):
    text = normalise(question)
    if re.search(r"\b(profit|income|expenses?|ledger|budget)\b", text):
        return "finance"
    if re.search(r'\b(buyers?|sell|selling)\b|produce markets', text):
        return 'produce'
    if re.search(r"\b(price|buy|purchase|order|supplier|discount|catalogue|marketplace)\b", text) or re.search(
            r"\b(where|how)\b.*\b(get|find|source)\b.*\b(seeds?|fertilisers?|fertilizers?)\b|recommendations?|opportunities|recommend.*\b(products?|suppliers?|seeds?)\b", text):
        return "buying"
    if re.fullmatch(r"(crop(s)? )?seeds?", text):
        return "buying"
    if followup(question, messages) and not re.search(r"\b(germination|water|irrigation|soil|plant|grow|harvest)\b", text):
        return previous_route or "farming"
    return "farming"


def seed_shopping(question, messages):
    text = normalise(question)
    users = [normalise(m["text"]) for m in messages if m["role"] == "user"]
    if re.search(r"\bseeds?\b", text):
        return True
    if followup(question, messages):
        for user in reversed(users):
            if re.search(r"\b(seeds?|fertilis\w*|fertiliz\w*|water\w*|expenses?|income)\b", user):
                return bool(re.search(r"\bseeds?\b", user))
    return False


def catalogue_reply(question, sources, messages):
    if not seed_shopping(question, messages):
        return None
    text = normalise(question)
    if re.fullmatch(r"(which|what) location( do you mean)?", text):
        return ("I mean the town or province where your farm is, not the supplier's address. "
                "That helps narrow down suitable seed and delivery options. Which town is your farm near?")
    products = []
    crop_text = text
    if followup(question, messages):
        crop_text = " ".join(m["text"].lower() for m in messages if m["role"] == "user") + " " + text
    crops = re.findall(r"\b(maize|tomato|bean|cabbage|onion|spinach|wheat|sunflower)s?\b", crop_text)
    crop = crops[-1] if crops else None
    # Plain answers to our crop question are user-supplied crop names, not verified variety advice.
    history = [*messages, {"role": "user", "text": question}]
    for before, after in zip(history, history[1:]) if not crops else ():
        if before['role'] == 'assistant' and after['role'] == 'user' and 'which crop' in before['text'].lower():
            answer = normalise(after['text'])
            if (followup(after['text'], [before])
                    and not re.match(r"^(which|what|why|where|how|when|can|should)\b", answer)
                    and not re.fullmatch(r"(crop(s)? )?seeds?", answer)):
                crop = answer
    last_reply = messages[-1]['text'].lower() if messages and messages[-1]['role'] == 'assistant' else ''
    location_supplied = bool(crop and ((('which town' in last_reply or 'your farm near' in last_reply)
                                      and followup(question, messages)) or re.search(r'\bin\s+\w+', text)))
    for source in sources:
        if not source["id"].startswith("product:"):
            continue
        try:
            item = json.loads(source["content"])
            price = Decimal(str(item["listedPriceZar"]))
            if not price.is_finite() or price < 0:
                continue
            name = str(item["productName"])[:160]
            supplier = str(item["supplierName"])[:160]
            if not re.search(r"\bseeds?\b", name, re.I) or (crop and crop not in name.lower()):
                continue
            products.append(f"- {name}, supplier: {supplier}, listed price: R{price:.2f}.")
        except (ValueError, TypeError, KeyError, InvalidOperation):
            continue
    if products:
        return ("These matching seed listings are recorded in AgriTech's Marketplace:\n"
                + "\n".join(products) + "\nOpen Marketplace to review the supplier and pack size. "
                "These are catalogue matches, not confirmed stock or variety suitability. "
                "Confirm availability and delivery with the supplier before buying.")
    if re.fullmatch(r"(crop(s)? )?seeds?", text):
        return ("Crop seeds are planting seeds, such as maize, beans or vegetable seeds. "
                "I found no matching seed listing in the app's current catalogue. "
                "Which crop do you want seeds for?")
    return ("Start with AgriTech's Marketplace to check seed suppliers. I found no matching seed listing "
            "in the current app catalogue, so I can't name a verified supplier or price. "
            "Outside the app, try an agricultural supply shop, farmers' cooperative or nursery near you; "
                "confirm they sell seed for your crop. " +
                ("I can't verify a local supplier address from the available data." if location_supplied else
                 "Which town is your farm near?" if crop else "Which crop do you need seeds for?"))
