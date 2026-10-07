# AI recommendations and quality checks

## Buying recommendations

Recommendations are deterministic, not a newly trained model. They match the authenticated
farmer's expense item names and their own past group-order product names against open orders.
Generic words such as seed, bag and planting do not establish a crop match. Closed/full orders
and orders already joined by the farmer are excluded. No records means no personalised
suggestions. Suggestions are recomputed rather than duplicated in recommendation history.
Matching recorded purchases does not prove that a variety is suitable for the farm.

The API calculates ZAR totals with BigDecimal, rounds to cents, and quotes a requested quantity
only while the order has capacity. Price comparisons use the exact same listing. Cross-listing
unit comparisons require an exact matching product specification code and the same kg/l/unit
basis; pack sizes may differ. Codes and pack specifications are user-entered, not independently
verified. Missing specifications are not inferred from names.

Group discounts are user-entered, not supplier-confirmed. Indicative differences exclude unknown
delivery and payment terms. No final delivered total or guaranteed savings is claimed.
Joining requires explicit confirmation on the group-order page; the AI never orders automatically.
For production deployments without Hibernate schema update, apply
`scripts/migrate-ai-comparison-fields.sql` using the normal database migration process.

## Knowledge and citations

`src/main/resources/knowledge/farming.properties` contains short editorial summaries of FAO
water and conservation guidance with original URLs, publisher, region, source-check dates and
honest unknown publication dates. App guides remain in FarmKnowledgeService. PostgreSQL
full-text retrieval supplies relevant passages to the existing LangChain/Qwen service.
The response includes citation metadata and the UI links to the original published sources.
General guidance is not a South African field-specific prescription. Agronomist review is pending.
Adding documents does not train or fine-tune Qwen.
Requests for unavailable stock/delivery facts, private information about other farmers, and
automatic purchases receive bounded backend responses rather than model-generated claims.

## Automated regression evaluation

Run offline adapter and evaluator tests:

```bash
cd langchain-service
python -m unittest discover -s tests -v
```

Run the 12-case live evaluation against a backend with its AI stack running:

```bash
export AI_EVAL_TOKEN='<JWT for a synthetic test account>'
python evaluation/evaluate.py --url http://127.0.0.1:18080
```

Use a synthetic account, not real farmer data. Remote evaluations require HTTPS. JWTs are
read only from the environment. Reports contain check results, not tokens, private ledger context,
or model answers. Nonzero exit status indicates a regression or an upstream failure.
The existing Python CI job discovers the offline evaluator tests automatically.

The dataset covers seed meaning, irrigation context, crop rotation, fertiliser comparisons,
profit, missing stock/delivery facts, unconfirmed discounts, no automatic transactions,
off-topic questions, instruction injection and private data.
Checks measure nonempty answers, relevant keywords, expected retrieved source IDs and known
forbidden claims. Citation presence is retrieval coverage, not proof every sentence is grounded.
These heuristics can miss hallucinations and can flag valid paraphrases: inspect failures and
add human-reviewed examples. Do not describe a passing score as guaranteed factual accuracy.
