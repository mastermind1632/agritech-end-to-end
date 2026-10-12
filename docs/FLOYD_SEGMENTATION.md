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
- Profile/consent changes invalidate use of an outdated cohort. Consent revocation removes
  search/impression history and local fitted artifacts; snapshots are rebuilt. Model promotion
  and training serialize with revocation. A deletion removes the profile and its own listings/activity.
- Expense targeting reads only the signed-in farmer's aggregated totals for the last two full
  months, and only with explicit expense consent. Expense features are not used in shared models.
- Optional deterministic control/exploration experiment reserves one qualified match slot for an
  under-exposed buyer request. Tracking requires analytics consent. Reports distinguish engagement
  from verified orders; no order-conversion rate is fabricated.
- Reviewed market-price CSV import with source URLs, kg units and observation dates. Summaries
  use volume-weighted historical observations. Forecasts use observed monthly market volumes:
  seasonal averages initially, Holt-Winters only with 24 consecutive observed months. Missing
  periods are not silently replaced with zero; coverage and forecast quality need human review.
- Drift checks monitor standardized feature means and crop distribution changes. New models
  remain candidates until reviewed. Automated tests cover features, matching, real clustering,
  validation, authentication, ownership, consent and PostgreSQL persistence.

## Running the stack

Set `POSTGRES_PASSWORD` and a strong `APP_JWT_SECRET` in the project's existing `.env`.
Keep the service internal; it does not expose PostgreSQL, a training API or MLflow publicly.

```sh
docker compose -f docker-compose.yml -f docker-compose.segmentation.yml up -d --build
```

For production, include the existing production and AI overlays:

```sh
docker compose -f docker-compose.prod.yml -f docker-compose.ai.yml -f docker-compose.segmentation.yml -f docker-compose.https.yml up -d --wait
```

The GitHub workflow tests the segmentation service against an isolated PostgreSQL database,
builds AMD64/ARM64 images and includes the overlay in deployment on a successful main push.
This is deployment configuration, not evidence that this new release has already been published.
Back up both `postgres_data` and `segmentation_data`; test restores and disk capacity before release.
Production currently uses HTTP: HTTPS must be configured before collecting real GPS, banking
consent or other sensitive information. Consent controls alone are not a POPIA compliance certification.

The prepared HTTPS configuration retains port 8080 and uses a trusted IP certificate. Port 443
is currently blocked by the cloud firewall. The alternative certificate-maintenance method
uses a trusted ephemeral container with Docker-socket access to publish and remove one public
ACME validation token in the existing port-80 frontend; it does not reconfigure or restart that
application. This method requires explicit owner approval before execution. Its path and
project restrictions are covered by tests. Certificate renewal must be scheduled before release;
short-lived certificates must not depend on a person remembering to renew them.

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
5. Integrate a completed-produce-order event from the full marketplace. At present buyer
   requests, supplier input purchases and contact clicks are not completed produce orders.
6. After enough verified orders exist, build collaborative filtering or learning-to-rank and
   compare it with the frozen rule baseline using offline temporal splits and a controlled A/B test.
   This data-dependent upgrade is not implemented or proven by the current pilot.
7. Validate forecasts with rolling backtests against seasonal averages, including error bars
   and coverage checks, before using them for commercial decisions.
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
