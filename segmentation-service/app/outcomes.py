"""Completed produce events from a trusted order system, never end-user feedback."""

import hmac
import os
from datetime import datetime, timezone
from decimal import Decimal
from typing import Literal
from uuid import UUID

from fastapi import APIRouter, Depends, Header, HTTPException, Response
from pydantic import AwareDatetime, BaseModel, ConfigDict, Field, model_validator

from app.models import CROPS, PROVINCES


class CompletedProduceOrder(BaseModel):
    model_config = ConfigDict(extra="forbid", allow_inf_nan=False)

    eventId: UUID
    orderId: UUID
    farmerId: UUID
    buyerId: UUID
    recordId: UUID
    crop: str
    province: str
    quantityKg: Decimal = Field(gt=0, le=1000000000, max_digits=15, decimal_places=3)
    pricePerKg: Decimal = Field(ge=0, le=1000000, max_digits=11, decimal_places=4)
    occurredAt: AwareDatetime
    impressionId: UUID | None = None
    status: Literal["completed"] = "completed"

    @model_validator(mode="after")
    def valid_completed_event(self):
        if self.crop not in CROPS or self.province not in PROVINCES:
            raise ValueError("Choose a supported crop and province")
        if self.farmerId == self.buyerId:
            raise ValueError("Farmer and buyer must be different accounts")
        if self.occurredAt > datetime.now(timezone.utc):
            raise ValueError("Completed order events cannot occur in the future")
        self.occurredAt = self.occurredAt.astimezone(timezone.utc)
        return self


def trusted_producer(x_segmentation_event_token: str = Header(default="", alias="X-Segmentation-Event-Token")):
    configured = os.getenv("SEGMENTATION_EVENT_TOKEN", "")
    if not configured:
        raise HTTPException(503, "Completed order event ingestion is disabled")
    if not hmac.compare_digest(x_segmentation_event_token.encode(), configured.encode()):
        raise HTTPException(401, "Trusted order producer authentication required")


def create_router(store):
    router = APIRouter()

    @router.post("/internal/order-outcomes", status_code=201, dependencies=[Depends(trusted_producer)])
    def completed_order(body: CompletedProduceOrder, response: Response):
        try:
            result = store.record_order_outcome(body.model_dump(mode="json"))
        except ValueError as exc:
            raise HTTPException(409, str(exc)) from exc
        if result["duplicate"]:
            response.status_code = 200
        return result

    return router
