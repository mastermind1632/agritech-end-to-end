from datetime import date
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, model_validator

CROPS = ("maize", "tomato", "spinach", "butternut", "potato", "cabbage", "onion",
         "carrot", "bean", "wheat", "sorghum", "soybean", "citrus", "apple", "grape", "avocado")
PROVINCES = ("Eastern Cape", "Free State", "Gauteng", "KwaZulu-Natal", "Limpopo",
             "Mpumalanga", "North West", "Northern Cape", "Western Cape")


class Location(BaseModel):
    model_config = ConfigDict(extra="forbid", allow_inf_nan=False)
    province: Literal["Eastern Cape", "Free State", "Gauteng", "KwaZulu-Natal", "Limpopo",
                      "Mpumalanga", "North West", "Northern Cape", "Western Cape"]
    district: str = Field(min_length=2, max_length=100)
    latitude: float = Field(ge=-35, le=-22)
    longitude: float = Field(ge=16, le=33)


class FarmProfile(Location):
    crop: str
    hectares: float = Field(gt=0, le=1000000)
    capacityKg: float = Field(gt=0, le=1000000000)
    plantingMonth: int = Field(ge=1, le=12)
    harvestMonth: int = Field(ge=1, le=12)
    deliveryRadiusKm: float = Field(gt=0, le=2000, default=100)
    businessRegistered: bool = False
    consistentProduction: bool = False
    analyticsConsent: bool = False
    expenseConsent: bool = False

    @model_validator(mode="after")
    def known_crop(self):
        if self.crop not in CROPS:
            raise ValueError("Choose a crop from the crop list")
        self.district = self.district.strip()
        if len(self.district) < 2:
            raise ValueError("Provide a district")
        return self


class MarketRecord(Location):
    kind: Literal["offer", "demand"]
    crop: str
    quantityKg: float = Field(gt=0, le=1000000000)
    pricePerKg: float | None = Field(default=None, ge=0, le=1000000)
    availableFrom: date
    availableUntil: date
    displayName: str = Field(min_length=2, max_length=100)
    publicContact: str = Field(default="", max_length=100)

    @model_validator(mode="after")
    def valid_listing(self):
        if self.crop not in CROPS:
            raise ValueError("Choose a crop from the crop list")
        if self.availableUntil < self.availableFrom:
            raise ValueError("End date must not precede start date")
        if (self.availableUntil - self.availableFrom).days > 366:
            raise ValueError("Limit listings to one year")
        if self.kind == "offer" and self.pricePerKg is None:
            raise ValueError("A produce offer requires a price per kg")
        self.displayName = self.displayName.strip()
        if len(self.displayName) < 2:
            raise ValueError("Provide a public display name")
        return self


class Search(BaseModel):
    model_config = ConfigDict(extra="forbid")
    crop: str = Field(max_length=30)
    province: str = Field(max_length=30)
    kind: Literal["offer", "demand"] = "offer"


class Feedback(BaseModel):
    model_config = ConfigDict(extra="forbid")
    impressionId: str = Field(min_length=36, max_length=36)
    recordId: str = Field(min_length=36, max_length=36)
    action: Literal["click", "enquiry"]


class MarketPrice(BaseModel):
    model_config = ConfigDict(extra="forbid", allow_inf_nan=False)
    crop: str
    province: str
    market: str = Field(min_length=2, max_length=100)
    observedOn: date
    pricePerKg: float = Field(gt=0, le=1000000)
    volumeKg: float = Field(gt=0, le=1000000000)
    sourceUrl: str = Field(pattern=r"^https://", max_length=500)

    @model_validator(mode="after")
    def valid_observation(self):
        if self.crop not in CROPS or self.province not in PROVINCES:
            raise ValueError("Unknown crop or province")
        if self.observedOn > date.today():
            raise ValueError("Market observations cannot be in the future")
        return self
