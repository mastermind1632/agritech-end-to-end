"""Run `python -m app.diagnostics` for aggregate, non-training model diagnostics."""
from importlib import import_module
from importlib.metadata import version
import json


def diagnose():
    checks = {}
    for module, distribution in (("numpy", "numpy"), ("pandas", "pandas"), ("sklearn.cluster", "scikit-learn"),
                                 ("kmodes.kprototypes", "kmodes"), ("psycopg", "psycopg"), ("mlflow", "mlflow-skinny")):
        try:
            import_module(module)
            checks[distribution] = {"available": True, "version": version(distribution)}
        except Exception as exc:
            checks[distribution] = {"available": False, "errorType": type(exc).__name__}
    result = {"dependencies": checks, "readOnly": True}
    if not all(check["available"] for check in checks.values()):
        result["status"] = "dependency_error"
        return result
    try:
        from app.readiness import model_readiness
        from app.store import Store
        store = Store()
        result["segmentationStatus"] = model_readiness(store.profiles(), training_job=store.job("monthly_training"),
                                                      worker_job=store.job("worker_status"))
        for field in ("activeModel", "candidate"):
            result["segmentationStatus"].get(field, {}).pop("reviewStatus", None)
        result["status"] = "ok"
    except Exception as exc:
        result.update(status="diagnostic_failed", errorType=type(exc).__name__)
    return result


if __name__ == "__main__":
    print(json.dumps(diagnose(), indent=2))
