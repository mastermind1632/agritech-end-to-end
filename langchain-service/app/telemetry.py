"""Allowlisted graph spans only: no prompts, answers, tokens, farmer IDs or checkpoint config."""
from contextlib import contextmanager
from contextvars import ContextVar
from datetime import datetime, timezone
import hashlib
import os
from uuid import uuid4

from langsmith import Client

_root = ContextVar("trace_root", default=None)
_session = ContextVar("anonymous_trace_session", default=None)


class Telemetry:
    def __init__(self, client=None, enabled=None):
        requested = os.getenv("AI_LANGSMITH_TRACING", "false").lower() == "true"
        self.enabled = enabled if enabled is not None else requested and bool(os.getenv("LANGSMITH_API_KEY"))
        self.client = client or (Client(hide_inputs=True, hide_outputs=True) if self.enabled else None)
        self.project = os.getenv("LANGSMITH_PROJECT", "agritech-graph")

    @contextmanager
    def conversation(self, thread):
        token = _session.set(hashlib.sha256(thread.encode()).hexdigest())
        try:
            yield
        finally:
            _session.reset(token)

    @contextmanager
    def span(self, name, run_type="chain"):
        if not self.enabled:
            yield
            return
        run = uuid4()
        now = datetime.now(timezone.utc)
        parent = _root.get()
        order = now.strftime("%Y%m%dT%H%M%S%fZ") + str(run)
        if parent:
            order = parent[2] + "." + order
        trace = parent[0] if parent else run
        token = _root.set((trace, run, order)) if not parent else None
        created = False
        failed = False
        try:
            try:
                self.client.create_run(name=name, inputs={}, run_type=run_type, id=run,
                    start_time=now, trace_id=trace, parent_run_id=parent[1] if parent else None,
                    dotted_order=order, project_name=self.project,
                    extra={"metadata": {"workflow": "agritech", "payload_redacted": True,
                        **({"session_id": _session.get()} if _session.get() else {})}})
                created = True
            except Exception:
                pass
            yield
        except Exception as exc:
            failed = type(exc).__name__ != "GraphInterrupt"
            raise
        finally:
            if created:
                try:
                    self.client.update_run(run, end_time=datetime.now(timezone.utc), outputs={},
                        error="Workflow step failed" if failed else None)
                except Exception:
                    pass
            if token is not None:
                _root.reset(token)
