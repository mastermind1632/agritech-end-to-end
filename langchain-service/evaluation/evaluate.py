"""Local regression checks, not a substitute for expert judgement or semantic grounding review."""
import argparse
import json
import os
from pathlib import Path
import sys
from urllib.parse import urlparse
from urllib.request import Request, urlopen

CASES = Path(__file__).with_name("cases.json")


def grade(case, reply):
    answer = str(reply.get("response", "")).lower()
    ids = [s.get("id", "") for s in reply.get("sources", [])]
    expected = case["sourcePrefixes"]
    return {
        "nonempty": bool(answer.strip()),
        "relevance_keywords": any(word.lower() in answer for word in case["anyWords"]),
        "retrieval_support": any(i.startswith(prefix) for i in ids for prefix in expected)
            if expected else not ids,
        "forbidden_claims_absent": not any(word.lower() in answer for word in case["forbidden"]),
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--url", default="http://127.0.0.1:18080")
    args = parser.parse_args()
    url = urlparse(args.url)
    if url.scheme not in ("http", "https") or not url.hostname or url.username or url.password:
        parser.error("Use an HTTP(S) backend URL without credentials")
    if url.scheme == "http" and url.hostname not in ("127.0.0.1", "localhost", "::1"):
        parser.error("Remote evaluations require HTTPS")
    token = os.environ.get("AI_EVAL_TOKEN")
    if not token:
        parser.error("Set AI_EVAL_TOKEN to a synthetic test account JWT; never use a real farmer account")
    results = []
    for case in json.loads(CASES.read_text(encoding="utf-8")):
        request = Request(args.url.rstrip("/") + "/api/ai/chat",
            data=json.dumps({"prompt": case["prompt"]}).encode(),
            headers={"Content-Type": "application/json", "Authorization": "Bearer " + token},
            method="POST")
        try:
            with urlopen(request, timeout=240) as response:
                checks = grade(case, json.load(response))
            results.append({"id": case["id"], "checks": checks, "passed": all(checks.values())})
        except Exception as exc:
            # Do not write prompts, tokens, account context, or model answers to the report.
            results.append({"id": case["id"], "passed": False, "error": type(exc).__name__})
    print(json.dumps({"passed": sum(r["passed"] for r in results), "total": len(results),
        "limitation": "Keyword and retrieval checks are proxies, not proof of factual correctness.",
        "results": results}, indent=2))
    return 0 if all(r["passed"] for r in results) else 1


if __name__ == "__main__":
    sys.exit(main())
