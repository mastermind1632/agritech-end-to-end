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
    parser.add_argument("--langsmith", action="store_true", help="Publish the synthetic dataset and boolean evaluator results")
    args = parser.parse_args()
    url = urlparse(args.url)
    if url.scheme not in ("http", "https") or not url.hostname or url.username or url.password:
        parser.error("Use an HTTP(S) backend URL without credentials")
    if url.scheme == "http" and url.hostname not in ("127.0.0.1", "localhost", "::1"):
        parser.error("Remote evaluations require HTTPS")
    token = os.environ.get("AI_EVAL_TOKEN")
    if not token:
        parser.error("Set AI_EVAL_TOKEN to a synthetic test account JWT; never use a real farmer account")
    cases = json.loads(CASES.read_text(encoding="utf-8"))
    if args.langsmith:
        if not os.environ.get("LANGSMITH_API_KEY"):
            parser.error("LangSmith upload requires LANGSMITH_API_KEY configured privately")
        from langsmith import Client, evaluate
        client = Client()
        dataset_name = "agritech-farming-regression-v1"
        if not client.has_dataset(dataset_name=dataset_name):
            dataset = client.create_dataset(dataset_name, description="Synthetic prompts; evaluator outputs contain checks only")
            client.create_examples(examples=[{"inputs": {"case": case},
                "outputs": {"nonempty": True, "relevance_keywords": True, "retrieval_support": True,
                    "forbidden_claims_absent": True}} for case in cases], dataset_id=dataset.id)
        def target(inputs):
            # Only boolean results leave this process, never the answer or verified ledger context.
            try:
                return grade(inputs["case"], ask(args.url, token, inputs["case"]["prompt"]))
            except Exception:
                return {key: False for key in ("nonempty", "relevance_keywords", "retrieval_support", "forbidden_claims_absent")}
        def evaluator(run, example):
            return {"key": "regression_pass", "score": int(bool(run.outputs) and all(run.outputs.values()))}
        results = evaluate(target, data=dataset_name, evaluators=[evaluator], client=client,
            experiment_prefix="agritech-graph", max_concurrency=1,
            description="Synthetic questions, private payloads excluded, keyword checks not proof of correctness")
        results.wait()
        passed = sum(all(row["run"].outputs.values()) for row in results)
        print(json.dumps({"passed": passed, "total": len(cases), "langsmith": True}))
        return 0 if passed == len(cases) else 1
    results = []
    for case in cases:
        try:
            checks = grade(case, ask(args.url, token, case["prompt"]))
            results.append({"id": case["id"], "checks": checks, "passed": all(checks.values())})
        except Exception as exc:
            # Do not write prompts, tokens, account context, or model answers to the report.
            results.append({"id": case["id"], "passed": False, "error": type(exc).__name__})
    print(json.dumps({"passed": sum(r["passed"] for r in results), "total": len(results),
        "limitation": "Keyword and retrieval checks are proxies, not proof of factual correctness.",
        "results": results}, indent=2))
    return 0 if all(r["passed"] for r in results) else 1


def ask(url, token, prompt):
    def call(path, body=None, method="POST"):
        request = Request(url.rstrip("/") + path,
            data=json.dumps(body).encode() if body is not None else None,
            headers={"Content-Type": "application/json", "Authorization": "Bearer " + token}, method=method)
        with urlopen(request, timeout=240) as response:
            return json.load(response)
    thread = call("/api/ai/conversations")["conversationId"]
    try:
        return call("/api/ai/conversations/" + thread + "/turn", {"prompt": prompt})
    finally:
        call("/api/ai/conversations/" + thread, method="DELETE")


if __name__ == "__main__":
    sys.exit(main())
