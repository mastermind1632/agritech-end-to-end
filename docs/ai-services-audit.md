# AI services audit — 19 October 2026

This records the initial audit. The subsequent [Floyd delivery](FLOYD_TASKS_STATUS.md) supersedes
its model-availability, worker-recovery, order-attribution and forecast-validation limitations.
The later [live Docker chat verification](#live-docker-chat-verification) also supersedes the
initial inability to verify Qwen connectivity. Earlier test counts below describe their original runs.

The recommendation, segmentation and analytics implementations were exercised locally,
including actual clustering and an isolated PostgreSQL database. Passing these checks
establishes implementation behavior on the tested cases; it does not establish field accuracy
or prove that a deployed release is healthy. No deployment or production data was changed.

## What each service actually does

| Feature | Implementation | Assessment |
| --- | --- | --- |
| Input buying recommendations | Java rules match the farmer's recorded items to available group orders, with unpersonalised exploration when there is no match. | Purchase matching, exclusion rules and indicative price calculations have automated coverage. A valid quote must remain accessible even outside the ten displayed suggestions. |
| Spending-based farmer clusters | Java K-means over five scaled lifetime spending/activity features. | Real clustering, with duplicate-profile handling corrected. These groups describe recorded activity, not crop suitability or farm production. |
| Similar farmers and spending alerts | Weighted numerical/token similarity and population Z-scores. | Existing tests cover expected matches and outliers. Similarity is not a calibrated probability; alerts compare farmers, not changes in the same farmer over time. |
| Produce buyer matches | Rules require matching crop, harvest dates, delivery radius and seasonal capacity. | Verified through FastAPI with real PostgreSQL records. Separate matches do not reserve inventory or allocate capacity across buyers. |
| Production-profile segmentation | Scikit-learn K-means, K-Prototypes and geographic HDBSCAN. | Actual fitting and quality gates are tested. At least 30 consented profiles, acceptable clusters, and named human review are required before activation. |
| Demand and forecasts | Recorded buyer requests/searches and imported market volumes; seasonal averages or Holt-Winters. | Aggregation and forecast input checks are tested. Stale, unfinished and incomplete histories cannot establish a current forecast. Forecast error against real outcomes has not been established. |
| Farming conversations | Authenticated LangGraph/LangChain workflow with Ollama/Qwen and retrieved application facts. | Initially offline-only; subsequent live Docker checks verified desktop/mobile replies, citations and saved history. All 12 synthetic live regression cases pass. This is bounded regression evidence, not proof of general answer accuracy. |

## Verified defects corrected

- Java K-means could report the requested number of clusters even when duplicate feature
  vectors left some groups empty. Seeding and the reported cluster count now reflect populated groups.
- A valid group-order quote could return 404 merely because it ranked below the ten displayed
  recommendations. Quote availability is checked independently of the display limit.
- Farming questions such as “Recommendations for irrigation” could route to buying suggestions.
  Routing now distinguishes farming subjects from generic or product recommendations.
- LangChain health checked only whether Ollama responded, allowing a missing configured model
  to look healthy. It now checks that model and returns 503 when unavailable.
- Forecasting could treat the current partial month as complete or return historical dates as a
  current outlook. It now uses completed months and requires history through the previous month.
- K-Prototypes silhouette evaluation used a different feature distance from the fitted algorithm.
  Evaluation now uses its squared numerical distance plus the fitted categorical mismatch weight.
  Silhouette calculations use at most 2,000 deterministically sampled profiles to bound pairwise work.
- Search, impression and feedback writes could race consent revocation, retaining analytics or
  failing with a foreign-key error. Per-profile transaction locks now serialize consent checks
  with revocation; a real PostgreSQL concurrency regression covers all three writes.

Regression coverage was added for these behaviors, training consent requirements, rejection
of identical-profile candidates, and the API transition from rules to reviewed K-means and back
to rules when the cohort changes.

## Validation scope

- Java: **39 tests passed**, with no failures, errors or skips. The installed Maven executable
  and Java 21 were used because the Windows wrapper failed while resolving its cached Maven path.
- LangChain: **51 tests passed**. Its dependency check also passed; all 13 pinned segmentation
  dependencies matched the installed versions.
- Segmentation: **35 tests passed**, with PostgreSQL enabled and no failures or skips.
- The LangChain suite exercises the real agent and graph with a fake LLM/backend, including
  conversation ownership, memory, routing and approval behavior.
- The segmentation suite fits real K-means, K-Prototypes and HDBSCAN models and records an
  actual local SQLite MLflow experiment. Isolated PostgreSQL checks exercise persistence,
  consent, impressions, feedback and ownership.
- An additional FastAPI/PostgreSQL integration run passed startup/health, persisted production
  profiles, buyer matching, coordinate redaction, owner spoofing rejection, cross-account deletion
  rejection and unauthenticated access rejection. Only identity resolution was substituted;
  no deployed Spring authentication or model generation was involved.
- Frontend: **2 tests passed** and the production build succeeded.
- Local Spring and segmentation default HTTP endpoints did not respond as running applications.
  Ollama on port 11434 and LangChain on port 8001 closed HTTP connections without a response,
  including checks outside the sandbox. This prevents a claim that the complete live AI stack works.

Together these suites passed **127 tests**. A deliberately separated synthetic cohort of 45
profiles also recovered its three planted groups of 15 with all three clustering algorithms:
K-means silhouette 0.99865, K-Prototypes 0.99999 and HDBSCAN 0.99939, with no noise and an
adjusted Rand index of 1.0. Each silhouette uses its algorithm's evaluation distance, so these
scores should not be compared as if they used the same metric. The easy synthetic dataset
demonstrates functioning algorithms, not accuracy on real farms.

## Priorities before relying on model quality

1. Start the local AI stack and run the existing live conversation evaluation with a synthetic
   account. Review actual answers and citations; the offline fake model cannot measure hallucinations.
2. Compare several K values and measure cluster stability on representative, consented profiles.
   Have an agronomist review the resulting groups. Synthetic separation and silhouette scores
   alone do not establish useful segments. The bounded silhouette score is an approximation;
   inspect small clusters separately because uniform sampling may omit them.
3. Account for the production model lifecycle: the API serves saved cohort assignments. A changed
   cohort falls back to rules until retraining and human promotion; it does not automatically predict
   new farmers with the old model. Monitor how often that fallback occurs.
4. Evaluate buying/matching precision from consented feedback and verified outcomes. Current buyer
   ranking remains rule-based even while the farmer's segment is labelled K-means.
5. Review lifetime spending features and generic-word matching in the older similarity system.
   Different farm size, account age and extreme expenses can dominate comparisons. Use temporal
   baselines if alerts are intended to detect a change in an individual farmer's spending.
6. Backtest forecasts against seasonal averages using chronological splits, data-coverage checks
   and error measures before making commercial claims.

## Frontend response verification

The follow-up browser run exercised the actual React application with synthetic HTTP responses,
at 1440 px and 390 px. All **26 browser checks** passed, with no JavaScript errors or page-wide
horizontal overflow. This verifies response rendering and recovery behavior, not live service
connectivity, Qwen accuracy or real production latency.

Verified displays include buying opportunities and price quotes, cluster comparison totals,
chat replies and linked citations, the learned segment label, buyer matching reasons, service
suggestions, demand summaries, withheld forecast notices and missing-profile guidance.
Injected failures cover chat retry, quote refresh during a pending request, approval completion
with a failed history refresh, approval conflicts, feedback failure and market-service errors.
Listing search waits for its initial data load rather than racing it.

The frontend review corrected stuck quote controls, consumed approvals continuing to block chat,
missing conversation retry, feedback errors hiding valid matches, unrelated feedback from listing
contacts, stale listing/profile callbacks and profile prefill overwriting entered fields. Profile
operations disable their inputs while pending, and late profile saves cannot restore account state
after the form unmounts.

Shared API requests now abort after 30 seconds, with a 240-second allowance for conversation/chat
requests. Both an unanswered request and a stalled response body produce a recoverable error.
Caller cancellation is preserved. **All 6 frontend unit tests pass**, including four new client
regressions, and the final production build succeeds. The service suites above remain unchanged;
the combined automated suite count is now **131**.

Local browser evidence and the executable check are in `.ai-check/frontend-ai-responses.json`
and `.ai-check/frontend-ai-response-check.cjs`. These are local audit artifacts, not a CI browser
suite. The browser check uses an existing local Playwright installation and synthetic account
data; it creates no backend accounts, listings or orders.

## Live Docker chat verification

The reported **The farming workflow is unavailable** error came from starting the base and
segmentation overlays without `docker-compose.ai.yml`. Spring consequently tried to reach
LangChain on its own container's localhost. Starting all three overlays supplies the internal
LangChain/Ollama addresses and downloads the configured `qwen2.5:0.5b` model.

A second observed defect appeared when Spring was recreated: the running Nginx frontend retained
the backend's old container address and returned HTTP 502 despite direct API health being UP.
Both HTTP and HTTPS frontend configurations now re-resolve the backend through Docker DNS;
request paths, query parameters and the 240-second AI response timeout are preserved.
An isolated Docker check changed a synthetic backend's IP while keeping its old address occupied.
The same frontend container recovered in about four seconds without restarting; the API query
string and health path remained unchanged. Nginx syntax validation passed, and all temporary
containers and their network were removed. The ignored harness is `.ai-check/verify-nginx-dns.ps1`.

The rebuilt local frontend on `http://localhost:3001` passed real browser checks at **1440 px
and 390 px** through Nginx, Spring JWT authentication, LangGraph and Qwen, without mocked
responses. Tomato watering and irrigation recommendation questions produced relevant replies
with application and FAO source citations. Conversation history returned the same saved answer.
No browser JavaScript errors or unexpected failed API responses occurred. The first measured
reply took about **16 seconds**, and the next about **4 seconds** on this machine; these two
samples are not a performance benchmark or an expert assessment of advice accuracy.

The check created and then removed only its synthetic verification account and conversations.
Existing accounts, database contents and the other project's running services were preserved.
Local evidence is in ignored `.ai-check/chat-docker-live.cjs`,
`.ai-check/chat-docker-live-report.json` and the desktop/mobile screenshots.

The existing **12-case live evaluation initially passed 10 cases**. Fertiliser comparison
guidance competed with unrelated seed/order guidance, while a software question matched the
word "show" in a farming document. Product-specific buying questions now prioritize their own
guidance, and retrieval excludes conversational filler. Mixed-product comparisons and explicit
stock/delivery/discount questions retain their relevant sources and recorded catalogue facts.
The database query also avoids stemming query words twice, which previously broke matches for
terms such as "fertiliser". These changes preserve bound parameters and public-only catalogue access.

After rebuilding and restarting the final local services, **all 12 live regression cases pass**,
along with **four authenticated HTTP grounding probes** for fertiliser, seeds, irrigation and
the unrelated software question. Fertiliser advice now includes nutrient content, pack size,
transport and soil-test considerations; the software question receives the farming-topic redirect
without citations. An additional **10 read-only PostgreSQL semantic probes pass**, as do the
**58 Python tests** and **40 Java tests**. Keyword/retrieval checks remain limited proxies for
correctness and do not replace review by an agronomist.

The ignored `.ai-check/chat-docker-evaluation-initial-report.json` and
`.ai-check/chat-docker-evaluation-report.json` preserve the before/after results;
`.ai-check/chat-docker-evaluation.py` runs the live check and removes its synthetic account.
The isolated PostgreSQL probes are in `.ai-check/farm-knowledge-search-probe.sql` and `.json`.

Use the combined startup command in the [root guide](../README.md#run-with-docker), then refresh
the page or select **Retry conversation** if it was opened before the chat services were ready.
