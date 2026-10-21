# Floyd: Market Segmentation Delivery

## Scope and decisions

This module answers three decisions: which buyer requests suit this farm's crop, next harvest,
delivery radius and seasonal capacity; which farms have similar production characteristics;
and which platform service should a farmer explore next. It is separate from the Qwen chatbot
and the older spending-based buying-group clustering.

The existing marketplace sells supplier inputs and has group purchases. Those purchases are
not produce sales and must not be presented as buyer demand for harvested crops. This module
adds produce offers and buyer requests; the full marketplace order/delivery/payment lifecycle
remains the commerce team's responsibility. No recommendation places an order.

## Implemented

- Registration continues into a production-profile step. Existing farmers can complete it in
  Farmer Profile. One primary crop per profile in this version; hectares, seasonal kg capacity,
  province, district, coordinates, planting/harvest months and delivery radius are validated.
- Produce Markets contains buyer matches, searchable produce offers and buyer requests,
  own-listing publication/removal and demand summaries. Explicit public display name/contact
  are separate from private account details. Exact coordinates are never returned in public browsing.
- FastAPI on internal port 8002 verifies each bearer token with Spring's identity endpoint.
  Browser requests use the existing authenticated Spring gateway at `/api/market-intelligence`.
  Profiles, deletion and recommendation feedback are tied to the verified account, not a body farmer id.
- Rule matching requires the same crop, distance within radius, requested kg within stated
  capacity and overlap with the next harvest month. Requests are independent candidates;
  combined requests are not an allocation of stock. Listed prices are not verified sales prices.
- Rule service targeting covers small unregistered farms, potential export producers and private
  expense-trend guidance. Export suggestions are not certification. Expense increases are not
  claimed to be input-price inflation, because changing production volumes can also increase costs.
- PostgreSQL `market_intelligence` schema stores profiles, listings, consented searches and
  recommendation impressions/feedback, sourced market observations, snapshots and job status.
  The worker runs a daily snapshot and monthly candidate training; failed jobs retry every five minutes.
- K-Means uses one-hot crops/provinces, standardized area/capacity and sine/cosine months.
  K-Prototypes uses scaled numeric/cyclical features plus categorical crops/provinces.
  HDBSCAN uses GPS radians and haversine distance. Silhouette scores, cluster sizes and noise
  are recorded, with understandable segment names.
- Below 30 consented profiles, the service stays on rules. Candidate models require at least
  two clusters, at least five members per cluster and silhouette >= 0.1, then named human review
  before promotion. Those thresholds are pilot defaults, not proof of agronomic validity.
- MLflow records experiment parameters, version and aggregate evaluation. Private fitted models
  and identifier-to-cluster assignments stay in the local persistent volume. No private farmer
  records are uploaded to an external experiment tracker.
- Reviewed K-Means and K-Prototypes models predict segments for added consented profiles using
  saved preprocessing, known-category checks and empirical cluster-distance limits. Existing
  reviewed members remain active when profiles are added. Edits/removal of a training member
  require retraining; HDBSCAN leaves unseen profiles on rules. These checks are not calibrated probabilities.
- Consent revocation removes
  search/impression history and local fitted artifacts; snapshots are rebuilt. Model promotion
  and training serialize with revocation. A deletion removes the profile and its own listings/activity.
- Expense targeting reads only the signed-in farmer's aggregated totals for the last two full
  months, and only with explicit expense consent. Expense features are not used in shared models.
- Optional deterministic control/exploration experiment reserves one qualified match slot for an
  under-exposed buyer request. Tracking requires analytics consent. Reports distinguish engagement
  from verified orders; no order-conversion rate is fabricated.
- Trusted completed-produce-order ingestion is available at internal `POST /internal/order-outcomes`.
  It is disabled unless `SEGMENTATION_EVENT_TOKEN` is configured and requires that credential in
  `X-Segmentation-Event-Token`. The Spring browser gateway does not forward this route. UUID event/order
  retries are idempotent; participants, demand listing, crop, region, timestamp and any originating
  recommendation impression are validated. Both participants must consent to analytics. Revocation,
  profile deletion or source-demand deletion removes these analytics. Contact clicks, trusted producer
  reports of completed orders and independently verified payments are separate measures.
- Reviewed market-price CSV import with source URLs, kg units and observation dates. Summaries
  use volume-weighted historical observations. Forecasts use observed monthly market volumes:
  seasonal averages initially, then a seasonal-naive baseline after 24 completed contiguous months.
  At 36 months, bounded rolling chronological backtests compare Holt-Winters with that baseline;
  only a complete, better-performing model supersedes it. MAE/WAPE, coverage and empirical historical
  error ranges are returned. These ranges are not calibrated confidence bounds. Missing periods are
  not silently replaced with zero. Forecasts run in the nightly job and HTTP reads use its published
  snapshot, withholding stale or previous-month results. Real data coverage still needs review.
- Drift checks monitor standardized feature means and crop distribution changes. New models
  remain candidates until reviewed. Automated tests cover features, matching, real clustering,
  validation, authentication, ownership, consent and PostgreSQL persistence.
- The worker retries when the consented cohort changes, a cold-start cohort reaches the threshold,
  model artifacts disappear, or a revoked snapshot needs rebuilding. Snapshot and training failures
  retry independently; an unchanged rejected candidate is not refitted every five minutes. Snapshots
  summarize actual reviewed clusters with small-group suppression and dated demand/outcome totals.

## Running the stack

Set `POSTGRES_PASSWORD` and a strong `APP_JWT_SECRET` in the project's existing `.env`.
Keep the service internal; it does not expose PostgreSQL, a training API or MLflow publicly.

For the full local application, including AI Insights chat, Produce Markets segmentation
and the analytics worker, run from the repository root:

```sh
docker compose -f docker-compose.yml -f docker-compose.ai.yml -f docker-compose.segmentation.yml up -d --build
```

AI Insights chat needs LangChain/Ollama and the configured Qwen model. The first startup
downloads the model; wait until `langchain-service` is healthy, then use **Retry conversation**
if the page was opened before it was ready. Include all three files when updating this stack.

The default web/database host ports are 3000/5432. On the current development machine,
those ports are occupied, so `.env` uses `FRONTEND_PORT=3001` and `POSTGRES_PORT=15432`;
its web address is `http://localhost:3001`. These are machine-specific overrides; container
connections to PostgreSQL continue to use port 5432.

For Produce Markets and its worker with the base app only, this optional partial-stack
command omits the farming-chat services:

```sh
docker compose -f docker-compose.yml -f docker-compose.segmentation.yml up -d --build
```

Use the full command above when AI Insights chat is also required. The base app plus
farming chat alone can use `docker-compose.yml` and `docker-compose.ai.yml` without
the segmentation overlay; see the [root startup guide](../README.md#run-with-docker).

For a live HTTP test deployment, include the existing production and AI overlays:

```sh
docker compose -f docker-compose.prod.yml -f docker-compose.ai.yml -f docker-compose.segmentation.yml up -d --wait
```

The GitHub workflow tests the segmentation service against an isolated PostgreSQL database,
builds AMD64/ARM64 images and includes the overlay in deployment on a successful main push.
This is deployment configuration, not evidence that this new release has already been published.
Back up both `postgres_data` and `segmentation_data`; test restores and disk capacity before release.
The live test address is `http://84.8.135.181:8080`. Ports 80 and 443 are blocked by the cloud
firewall, so external IP-certificate validation currently fails. The UI warns that this is an
unencrypted test deployment: use synthetic data only. HTTPS must be configured before collecting
real GPS, banking information or other sensitive records. Consent controls alone are not a
POPIA compliance certification.

The prepared HTTPS configuration retains port 8080 and requires a trusted IP certificate. The certificate-maintenance method
uses a trusted ephemeral container with Docker-socket access to publish and remove one public
ACME validation token in the existing port-80 frontend; it does not reconfigure or restart that
application. The user approved this approach, but issuance is blocked by the cloud's port-80 rule. Its path and
project restrictions are covered by tests. Certificate renewal must be scheduled before release;
short-lived certificates must not depend on a person remembering to renew them. Certificate
maintenance is not enabled in the HTTP test release. After the cloud owner opens the required
port and a trusted certificate is issued, use the HTTPS overlay and `RELEASE_HTTPS=true`.

## Operations and demonstration

```sh
docker compose -f docker-compose.prod.yml -f docker-compose.segmentation.yml exec segmentation-service python -m app.jobs snapshot
docker compose -f docker-compose.prod.yml -f docker-compose.segmentation.yml exec segmentation-service python -m app.jobs train --algorithm kmeans
docker compose -f docker-compose.prod.yml -f docker-compose.segmentation.yml exec segmentation-service python -m app.jobs train --algorithm kprototypes
docker compose -f docker-compose.prod.yml -f docker-compose.segmentation.yml exec segmentation-service python -m app.jobs train --algorithm hdbscan
docker compose -f docker-compose.prod.yml -f docker-compose.segmentation.yml exec segmentation-service python -m app.jobs promote --reviewed-by "Named agronomist"
docker compose -f docker-compose.prod.yml -f docker-compose.segmentation.yml exec segmentation-service python -m app.jobs experiments
```

Enable the pilot experiment with `SEGMENTATION_AB_TEST=true` only after agreeing consent,
traffic allocation, sample size, monitoring and a stopping rule. It compares two rule-based
ranking variants; it does not claim a learned ranker has beaten the baseline.

For the trusted order producer, set `SEGMENTATION_EVENT_TOKEN` privately in both the producer and
segmentation service; never place it in frontend configuration. A completed event contains
`eventId`, `orderId`, `farmerId`, `buyerId`, `recordId`, `crop`, `province`, `quantityKg`,
`pricePerKg`, timezone-aware `occurredAt` and optional `impressionId`; `status` is `completed`.
The source must be an actual completed produce order from the commerce system. It must not be
a supplier-input group purchase, contact click, or invented farmer self-report. Events are retained
until a participant revokes/deletes their profile or the source demand is deleted; agree a production
retention period before connecting real data. The contract is implemented; the full commerce
team must connect its producer. This service does not create deliveries or verify payments.

Example pilot: register two test accounts, complete a tomato farm profile for the first, then
publish a tomato buyer request with a nearby location and matching harvest dates from the
second. The first should see the request with four explicit matching reasons. A different crop,
too-distant request, excessive quantity, non-overlapping date or own listing must not appear.
Run synthetic experiments only in an isolated development database, never as live farmer records.

## Market data import

Obtain an authorized export from the market/data owner. Do not scrape or claim a live Joburg
Market feed exists. Normalize product names, package weights and grades before importing.
Different grades and package units must not be combined into a misleading price benchmark.

CSV headers: `crop,province,market,observedOn,pricePerKg,volumeKg,sourceUrl`.
One normalized observation per crop/market/date; re-import is an upsert. The operator must
verify the source, units, coverage and provenance; a valid HTTPS URL alone is not verification.

```sh
# Mount the reviewed file into the container first, then:
python -m app.jobs import-prices --csv /imports/reviewed-market-prices.csv
```

## Remaining real-world work

1. Agree pilot crops, districts, matching decisions and acceptance criteria with the team.
2. Obtain real farmer/buyer consent and representative production profiles. Review the privacy
   notice, retention periods, server access and backup deletion with the responsible privacy lead.
3. Obtain authorized municipal-market observations and validate data collection coverage.
4. Have an agronomist/extension officer assess clusters and their plain-language names.
5. Connect the full marketplace's trusted completed-produce-order producer to the implemented
   internal ingestion contract. Buyer requests, supplier input purchases and contact clicks are
   not completed produce orders, and no live producer or real order dataset is assumed.
6. After enough verified orders exist, build collaborative filtering or learning-to-rank and
   compare it with the frozen rule baseline using offline temporal splits and a controlled A/B test.
   This data-dependent upgrade is not implemented or proven by the current pilot.
7. Run the implemented chronological backtests and coverage/error-range checks on reviewed,
   representative market observations before using forecasts for commercial decisions.
8. Load-test, apply HTTPS, inspect dependency/security updates and review server disk/memory
   before publishing the new images. Model jobs should not impair the existing finance/chat service.

## Evidence and tools

- Prototype: `segmentation-service/notebooks/segmentation.ipynb`.
- Run tests: `python -m unittest discover -s tests -v` inside `segmentation-service`.
  Set `SEGMENTATION_TEST_DATABASE=true`, PG connection variables and `MODEL_DIR` to an isolated
  test location to include real PostgreSQL tests. Never point those tests at production.
- Clustering: https://scikit-learn.org/stable/modules/clustering.html
- K-Prototypes: https://github.com/nicodv/kmodes
- Experiment tracking: https://mlflow.org/docs/latest/tracking/
- Seasonal forecasting: https://www.statsmodels.org/stable/generated/statsmodels.tsa.holtwinters.ExponentialSmoothing.html
