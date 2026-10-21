import os
from datetime import date, datetime, timedelta, timezone
from pathlib import Path
from uuid import uuid4

import psycopg
from psycopg.types.json import Jsonb


class Store:
    def connect(self):
        return psycopg.connect(host=os.getenv("PGHOST", "localhost"), port=int(os.getenv("PGPORT", "5432")),
                               dbname=os.getenv("PGDATABASE", "agritech"), user=os.getenv("PGUSER", "postgres"),
                               password=os.environ["PGPASSWORD"], connect_timeout=10,
                               options="-c statement_timeout=15000")

    def initialize(self):
        with self.connect() as conn:
            conn.execute("SELECT pg_advisory_xact_lock(68410201)")
            conn.execute(Path(__file__).with_name("schema.sql").read_text())

    def profile(self, owner):
        with self.connect() as conn:
            row = conn.execute("SELECT data FROM market_intelligence.profiles WHERE owner_id=%s", (owner,)).fetchone()
        return {**row[0], "ownerId": owner} if row else None

    def save_profile(self, owner, profile):
        with self.connect() as conn:
            # Serializes consent changes with analytics publication; no revoked profile can be republished.
            conn.execute("SELECT pg_advisory_xact_lock(68410202)")
            previous = conn.execute("SELECT data FROM market_intelligence.profiles WHERE owner_id=%s", (owner,)).fetchone()
            conn.execute("""INSERT INTO market_intelligence.profiles(owner_id,data) VALUES(%s,%s)
                ON CONFLICT(owner_id) DO UPDATE SET data=excluded.data,updated_at=now(),consent_updated_at=
                CASE WHEN market_intelligence.profiles.data->'analyticsConsent' IS DISTINCT FROM excluded.data->'analyticsConsent'
                  OR market_intelligence.profiles.data->'expenseConsent' IS DISTINCT FROM excluded.data->'expenseConsent'
                THEN now() ELSE market_intelligence.profiles.consent_updated_at END""", (owner, Jsonb(profile)))
            # Snapshots are aggregate-only, but remove them on revocation so fresh jobs reflect the new cohort.
            if not profile["analyticsConsent"]:
                if previous and previous[0]["analyticsConsent"]:
                    from app.training import invalidate_artifacts
                    invalidate_artifacts()
                    conn.execute("DELETE FROM market_intelligence.snapshots")
                conn.execute("DELETE FROM market_intelligence.searches WHERE owner_id=%s", (owner,))
                conn.execute("DELETE FROM market_intelligence.impressions WHERE owner_id=%s", (owner,))
                self._remove_order_outcomes(conn, owner)
        return {**profile, "ownerId": owner}

    def delete_profile(self, owner):
        with self.connect() as conn:
            conn.execute("SELECT pg_advisory_xact_lock(68410202)")
            previous = conn.execute("SELECT data FROM market_intelligence.profiles WHERE owner_id=%s", (owner,)).fetchone()
            self._remove_order_outcomes(conn, owner)
            for table in ("profiles", "searches", "impressions", "records"):
                conn.execute(f"DELETE FROM market_intelligence.{table} WHERE owner_id=%s", (owner,))
            if previous and previous[0]["analyticsConsent"]:
                conn.execute("DELETE FROM market_intelligence.snapshots")
                from app.training import invalidate_artifacts
                invalidate_artifacts()

    def with_cohort_lock(self, build):
        with self.connect() as conn:
            conn.execute("SELECT pg_advisory_xact_lock(68410202)")
            return build(self.profiles())

    def profiles(self):
        with self.connect() as conn:
            rows = conn.execute("SELECT owner_id,data FROM market_intelligence.profiles WHERE data->>'analyticsConsent'='true' ORDER BY owner_id").fetchall()
        return [{**data, "ownerId": owner} for owner, data in rows]

    def records(self, owner=None, crop=None, province=None, kind=None):
        with self.connect() as conn:
            rows = conn.execute("""SELECT id,owner_id,data FROM market_intelligence.records
                WHERE (%s::text IS NULL OR owner_id=%s) AND (%s::text IS NULL OR data->>'crop'=%s)
                AND (%s::text IS NULL OR data->>'province'=%s) AND (%s::text IS NULL OR data->>'kind'=%s)
                AND (%s::text IS NOT NULL OR (data->>'availableUntil')::date >= CURRENT_DATE)
                ORDER BY created_at DESC LIMIT 5000""",
                (owner, owner, crop, crop, province, province, kind, kind, owner)).fetchall()
        # Never disclose another account's exact GPS point or internal farmer id.
        return [{**data, "id": str(key), "ownerId": account} for key, account, data in rows]

    def save_record(self, owner, data):
        key = uuid4()
        with self.connect() as conn:
            conn.execute("SELECT pg_advisory_xact_lock(hashtext(%s))", (owner,))
            count = conn.execute("SELECT count(*) FROM market_intelligence.records WHERE owner_id=%s", (owner,)).fetchone()[0]
            if count >= 100:
                raise ValueError("Remove an old listing before adding another (100 per account)")
            conn.execute("INSERT INTO market_intelligence.records(id,owner_id,data) VALUES(%s,%s,%s)", (key, owner, Jsonb(data)))
        return {**data, "id": str(key), "ownerId": owner}

    def delete_record(self, owner, key):
        with self.connect() as conn:
            conn.execute("SELECT pg_advisory_xact_lock(68410202)")
            has_outcomes = conn.execute("""SELECT 1 FROM market_intelligence.order_outcomes o
                JOIN market_intelligence.records r ON r.id=o.record_id WHERE r.id=%s AND r.owner_id=%s LIMIT 1""",
                (key, owner)).fetchone()
            deleted = conn.execute("DELETE FROM market_intelligence.records WHERE id=%s AND owner_id=%s", (key, owner)).rowcount > 0
            if has_outcomes:
                conn.execute("DELETE FROM market_intelligence.snapshots")
            return deleted

    def log_search(self, owner, data):
        with self.connect() as conn:
            if not self._analytics_allowed(conn, owner):
                return
            conn.execute("INSERT INTO market_intelligence.searches(owner_id,data) VALUES(%s,%s)",
                         (owner, Jsonb({**data, "occurredOn": date.today().isoformat()})))

    @staticmethod
    def _analytics_allowed(conn, owner):
        # Keep the consenting profile locked until this event commits. Revocation/deletion must
        # either remove that committed event or complete before we check the updated consent.
        return conn.execute("""SELECT owner_id FROM market_intelligence.profiles
            WHERE owner_id=%s AND data->>'analyticsConsent'='true' FOR SHARE""", (owner,)).fetchone() is not None

    def searches(self):
        with self.connect() as conn:
            rows = conn.execute("SELECT data FROM market_intelligence.searches WHERE created_at >= now()-interval '365 days'").fetchall()
        return [row[0] for row in rows]

    def prices(self):
        with self.connect() as conn:
            return [r[0] for r in conn.execute("SELECT data FROM market_intelligence.prices ORDER BY observed_on").fetchall()]

    def import_prices(self, prices):
        with self.connect() as conn:
            for data in prices:
                conn.execute("""INSERT INTO market_intelligence.prices(crop,market,observed_on,data) VALUES(%s,%s,%s,%s)
                    ON CONFLICT(crop,market,observed_on) DO UPDATE SET data=excluded.data""",
                    (data["crop"], data["market"], data["observedOn"], Jsonb(data)))

    def expense_trend(self, owner):
        this_month = date.today().replace(day=1)
        last_month = (this_month - timedelta(days=1)).replace(day=1)
        previous = (last_month - timedelta(days=1)).replace(day=1)
        with self.connect() as conn:
            rows = conn.execute("""SELECT date_trunc('month',date)::date,sum(amount) FROM public.expenses
                WHERE farmer_id=%s AND date >= %s AND date < %s GROUP BY 1""", (owner, previous, this_month)).fetchall()
        totals = dict(rows)
        return {"current": float(totals.get(last_month, 0)), "previous": float(totals.get(previous, 0))}

    def impression(self, owner, arm, version, records):
        key = str(uuid4())
        with self.connect() as conn:
            if not self._analytics_allowed(conn, owner):
                return None
            conn.execute("INSERT INTO market_intelligence.impressions(id,owner_id,data) VALUES(%s,%s,%s)",
                         (key, owner, Jsonb({"arm": arm, "version": version, "recordIds": [r["id"] for r in records]})))
        return key

    def feedback(self, owner, data):
        with self.connect() as conn:
            if not self._analytics_allowed(conn, owner):
                return False
            row = conn.execute("SELECT data FROM market_intelligence.impressions WHERE id=%s AND owner_id=%s FOR SHARE", (data["impressionId"], owner)).fetchone()
            if not row or data["recordId"] not in row[0]["recordIds"]:
                return False
            conn.execute("""INSERT INTO market_intelligence.feedback(impression_id,record_id,action) VALUES(%s,%s,%s)
                ON CONFLICT DO NOTHING""", (data["impressionId"], data["recordId"], data["action"]))
        return True

    def exposure(self):
        with self.connect() as conn:
            rows = conn.execute("""SELECT record,count(*) FROM market_intelligence.impressions,
                jsonb_array_elements_text(data->'recordIds') AS record
                WHERE created_at >= now()-interval '30 days' GROUP BY record""").fetchall()
        return dict(rows)

    def experiment_report(self):
        with self.connect() as conn:
            rows = conn.execute("""SELECT i.data->>'arm',count(DISTINCT i.id),count(DISTINCT f.impression_id),
                       count(DISTINCT o.order_id),count(DISTINCT o.impression_id)
                FROM market_intelligence.impressions i LEFT JOIN market_intelligence.feedback f ON i.id=f.impression_id
                JOIN market_intelligence.profiles farmer ON farmer.owner_id=i.owner_id
                    AND farmer.data->>'analyticsConsent'='true'
                LEFT JOIN market_intelligence.order_outcomes o ON i.id=o.impression_id
                    AND EXISTS (SELECT 1 FROM market_intelligence.profiles buyer
                        WHERE buyer.owner_id=o.buyer_id AND buyer.data->>'analyticsConsent'='true')
                GROUP BY 1""").fetchall()
        return [{"arm": arm, "impressions": n, "engagedImpressions": clicks,
                 "engagementRate": clicks / n if n else 0, "verifiedOrders": None,
                 "trustedCompletedOrders": orders, "convertedImpressions": converted,
                 "recordedOrderConversionRate": converted / n if n else 0,
                 "orderEvidence": "Trusted producer reports of completed produce orders; payment is not independently verified"}
                for arm, n, clicks, orders, converted in rows]

    @staticmethod
    def _remove_order_outcomes(conn, owner):
        removed = conn.execute("DELETE FROM market_intelligence.order_outcomes WHERE farmer_id=%s OR buyer_id=%s",
                               (owner, owner)).rowcount
        if removed:
            conn.execute("DELETE FROM market_intelligence.snapshots")

    def record_order_outcome(self, data):
        # Canonicalize amounts and timestamps so an at-least-once producer can retry safely.
        from app.outcomes import CompletedProduceOrder
        validated = CompletedProduceOrder.model_validate(data)
        event = validated.model_dump(mode="json")
        event["quantityKg"] = format(validated.quantityKg.normalize(), "f")
        event["pricePerKg"] = format(validated.pricePerKg.normalize(), "f")
        with self.connect() as conn:
            # Same lock order as profile revocation and snapshot publication.
            conn.execute("SELECT pg_advisory_xact_lock(68410202)")
            profiles = dict(conn.execute("""SELECT owner_id,data FROM market_intelligence.profiles
                WHERE owner_id IN (%s,%s) ORDER BY owner_id FOR SHARE""",
                (event["farmerId"], event["buyerId"])).fetchall())
            if len(profiles) != 2 or not all(p.get("analyticsConsent") is True for p in profiles.values()):
                raise ValueError("Both participants must enable analytics consent before recording completed orders")
            existing = conn.execute("""SELECT event_id,data,impression_id FROM market_intelligence.order_outcomes
                WHERE event_id=%s OR order_id=%s FOR UPDATE""", (event["eventId"], event["orderId"])).fetchall()
            if existing:
                comparable = {k: v for k, v in event.items() if k != "eventId"}
                if len(existing) != 1 or {k: v for k, v in existing[0][1].items() if k != "eventId"} != comparable:
                    raise ValueError("The event or order identifier already has different completed order details")
                return {"recorded": True, "duplicate": True, "eventId": str(existing[0][0]),
                        "orderId": event["orderId"], "attributed": existing[0][2] is not None}
            record = conn.execute("SELECT owner_id,data FROM market_intelligence.records WHERE id=%s FOR SHARE",
                                  (event["recordId"],)).fetchone()
            if not record or record[0] != event["buyerId"] or record[1].get("kind") != "demand":
                raise ValueError("The completed order must reference this buyer's produce demand listing")
            if record[1].get("crop") != event["crop"] or record[1].get("province") != event["province"]:
                raise ValueError("Completed order crop and province must match the buyer demand listing")
            if profiles[event["farmerId"]].get("crop") != event["crop"]:
                raise ValueError("The farmer production profile must match the completed order crop")
            occurred = datetime.fromisoformat(event["occurredAt"].replace("Z", "+00:00"))
            local_day = occurred.astimezone(timezone(timedelta(hours=2))).date()
            if not (date.fromisoformat(record[1]["availableFrom"]) <= local_day
                    <= date.fromisoformat(record[1]["availableUntil"])):
                raise ValueError("Completed order date must fall within the buyer demand availability dates")
            if event["impressionId"]:
                impression = conn.execute("""SELECT data,created_at FROM market_intelligence.impressions
                    WHERE id=%s AND owner_id=%s FOR SHARE""", (event["impressionId"], event["farmerId"])).fetchone()
                if (not impression or event["recordId"] not in impression[0].get("recordIds", [])
                        or impression[1] > occurred):
                    raise ValueError("The recommendation impression must belong to the farmer and precede this buyer order")
            conn.execute("""INSERT INTO market_intelligence.order_outcomes
                (event_id,order_id,farmer_id,buyer_id,record_id,impression_id,data) VALUES(%s,%s,%s,%s,%s,%s,%s)""",
                (event["eventId"], event["orderId"], event["farmerId"], event["buyerId"], event["recordId"],
                 event["impressionId"], Jsonb(event)))
        return {"recorded": True, "duplicate": False, "eventId": event["eventId"],
                "orderId": event["orderId"], "attributed": event["impressionId"] is not None}

    def order_outcomes(self):
        with self.connect() as conn:
            rows = conn.execute("""SELECT o.data FROM market_intelligence.order_outcomes o
                JOIN market_intelligence.profiles f ON f.owner_id=o.farmer_id AND f.data->>'analyticsConsent'='true'
                JOIN market_intelligence.profiles b ON b.owner_id=o.buyer_id AND b.data->>'analyticsConsent'='true'
                ORDER BY o.data->>'occurredAt',o.event_id""").fetchall()
        return [{k: float(v) if k in ("quantityKg", "pricePerKg") else v for k, v in row[0].items()
                 if k in ("eventId", "orderId", "crop", "province", "quantityKg", "pricePerKg", "occurredAt", "status")}
                for row in rows]

    def publish_snapshot(self, build):
        with self.connect() as conn:
            conn.execute("SELECT pg_advisory_xact_lock(68410202)")
            data = build()
            conn.execute("""INSERT INTO market_intelligence.snapshots(snapshot_day,data) VALUES(CURRENT_DATE,%s)
                ON CONFLICT(snapshot_day) DO UPDATE SET data=excluded.data,created_at=now()""", (Jsonb(data),))
            conn.execute("DELETE FROM market_intelligence.searches WHERE created_at < now()-interval '365 days'")
            conn.execute("DELETE FROM market_intelligence.impressions WHERE created_at < now()-interval '90 days'")
            conn.execute("DELETE FROM market_intelligence.snapshots WHERE snapshot_day < CURRENT_DATE-90")
        return data

    def latest_snapshot(self):
        with self.connect() as conn:
            row = conn.execute("SELECT data FROM market_intelligence.snapshots ORDER BY snapshot_day DESC LIMIT 1").fetchone()
        return row[0] if row else None

    def job(self, name, data=None):
        with self.connect() as conn:
            if data is not None:
                conn.execute("""INSERT INTO market_intelligence.jobs(name,data) VALUES(%s,%s)
                    ON CONFLICT(name) DO UPDATE SET completed_at=now(),data=excluded.data""", (name, Jsonb(data)))
            row = conn.execute("SELECT completed_at,data FROM market_intelligence.jobs WHERE name=%s", (name,)).fetchone()
        return {"completedAt": row[0].isoformat(), "data": row[1]} if row else None
