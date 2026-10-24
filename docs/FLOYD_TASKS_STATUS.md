# Floyd delivery against the platform guide

Source: *Agritech Platform Five Functionalities Guide*, section 2, pages 5–6.
The document defines the requested work; statements elsewhere in it are not deployment approval.
This delivery is in the local project. Production deployment and real-world validation are separate.

| Floyd requirement | Delivered behavior | Evidence |
| --- | --- | --- |
| Agree decisions driven by segments | Buyer matching and business/export/expense service targeting have explicit criteria. | `app/engine.py`, module scope in `FLOYD_SEGMENTATION.md`; real API targeting checks. |
| Clean registration/listing data | Crop/province choices, hectares, kg capacity, planting/harvest months, validated coordinates, radius and explicit consent. | Production Profile and Produce Markets forms; Pydantic validation; actual profile save and listing publication. |
| Nightly analytics pipeline | Separate PostgreSQL schema, daily snapshots, independently retrying snapshot/training work, immediate rebuild after snapshot invalidation. | `app/store.py`, `schema.sql`, `jobs.py`; PostgreSQL and worker recovery tests. |
| Prototype and evaluate clustering | Jupyter prototype, actual K-means/K-Prototypes/HDBSCAN fitting, cyclical seasons, scaled numerical features, clustering metrics and quality gates. | Notebook, `training.py`, real fitted-model tests and local MLflow run. |
| Name segments understandably | Size, crop, province and harvest-month names; snapshots distinguish model version and cluster even when names coincide. | Model tests and real browser segment display. |
| Serve segments and recommendations | JWT authentication through Spring, internal FastAPI, privacy filtering, reviewed-model prediction for new supported profiles and transparent rules otherwise. | Real Spring → FastAPI → PostgreSQL checks and desktop/mobile browser flows. |
| Versioning and review | MLflow experiment/version records; explicit reviewer required before activation; unsafe/unknown/outlier inference falls back to rules. | Training/promotion and inference tests. Synthetic test promotion is not agronomic approval. |
| Monthly/seasonal maintenance and drift | Monthly scheduling, cohort-change/cold-start/missing-artifact retries, independent failures, drift reports and candidate review. | Worker tests and real same-day revocation recovery check. |
| Identify demand and markets | Separate open requests, unsuccessful searches, completed produce events and sourced historical prices by crop/province/YYYY-MM. | Demand tests, real attributed order aggregation and updated frontend table. |
| Forecast when enough data exists | Seasonal baseline after 24 complete contiguous months; chronological baseline/model comparison after 36; MAE/WAPE, coverage and empirical error ranges. | Actual statsmodels tests, published nightly forecast, frontend error display. Requests do not refit models. |
| Recommend buyers/products/opportunities | Crop, next harvest, radius and supply-capacity rules; existing supplier-input recommendations remain separate from produce sales. | Matching/exclusion tests and real buyer flow. Learned order ranking remains conditional on sufficient completed-order data, as the guide specifies. |
| Target appropriate platform services | Small unregistered farms, potentially export-suitable producers and consented expense increases receive distinct suggestions. | All three branches verified against real authenticated APIs. These are referrals, not completed registration or export certification. |
| Track recommendations leading to orders | Authenticated internal completed-order producer contract, validated attribution, idempotent events, consent removal and contact-versus-order metrics. | Endpoint and real PostgreSQL tests; real local HTTP attribution and replay check. Actual commerce producer connection remains necessary. |
| Cold start, exposure and consent | Honest rules fallback, optional qualified under-exposed candidate slot, explicit profile/expense consent, private GPS and revocation cleanup. | Rule/exploration tests, concurrency tests, real privacy and revocation checks. |

## Repairs made in this delivery

- Added actual new-profile prediction without disabling the unchanged reviewed training cohort.
- Repaired cold-start, missing-artifact and same-day snapshot recovery; training and snapshot
  failures no longer prevent one another from retrying.
- Made snapshots summarize reviewed clusters rather than merely repeating rule groups.
- Added trusted completed-produce event ingestion and recommendation attribution. Browser bearer
  tokens cannot manufacture completed-order outcomes.
- Added chronological forecast comparison and historical error ranges; demand history preserves
  the year, and completed orders remain separate from requests and contact clicks.
- Moved forecast fitting to scheduled analytics and withheld stale snapshots, including month rollover.
- Updated the frontend to show dated demand, completed quantities, historical forecast errors and ranges.
- Preserved listing creation HTTP 201 through the Spring gateway; feedback remains HTTP 200.

## Verification

- Segmentation: **82 tests passed with PostgreSQL enabled**, no skips or failures. This includes
  real clustering, local MLflow, forecasting, concurrency, ownership, consent and outcome ingestion.
- Spring: **40 tests passed** and the backend package built.
- Frontend: **6 tests passed** and the production build succeeded.
- The real local service flow used 46 consented synthetic training profiles, fitted/reviewed a test
  K-means candidate, then correctly predicted a 47th profile without disabling existing assignments.
  It verified matching exclusions, privacy, business/export/expense targeting, published backtested
  forecasts, completed-order attribution and duplicate replay handling.
- Real consent revocation removed outcome analytics and cached forecasts, restored rules and
  rebuilt the snapshot in the same day. Worker tests also cover South African/UTC daily and monthly
  schedule boundaries so stored offset timestamps do not cause repeated jobs.
- Real browser checks at **1440 px and 390 px** use React → Vite → Spring JWT authentication →
  FastAPI → PostgreSQL, without mocked responses. They cover buyer matches, feedback, service
  targeting, listing publication, dated demand, forecast errors/ranges and owned profile saving.
- Local evidence is in `.ai-check/floyd-final-tests.log`, `floyd-live-check.py`,
  `floyd-live-browser.cjs` and `floyd-live-browser-report.json`. These are ignored local audit
  artifacts. No production database or deployed application was changed.

## External and data-dependent work

The implementation does not invent real farmers, municipal prices, completed sales or expert
approval. Supply representative consented profiles, authorized municipal observations, a named
agronomist/extension review, and the commerce system's actual completed-produce event producer.
The guide postpones collaborative filtering/learning-to-rank until there are a few thousand orders;
the present rule baseline and attribution pipeline support collecting that evidence.

Market prices and gross recorded produce values do not establish profitability without production,
delivery and selling costs. Forecast backtests on synthetic examples establish working code, not
commercial accuracy. Business-building, export operations, full marketplace transactions and OCR
are separate functionalities in the guide, not completed by this segmentation module.

See [Floyd's operations guide](FLOYD_SEGMENTATION.md) for run commands, the internal event contract
and configuration. Deploy the changed services before expecting these repairs on a hosted release.
