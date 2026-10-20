from typing import Annotated, Literal
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field


MAX_SAFE_INTEGER = 9_007_199_254_740_991

PositiveId = Annotated[
    int,
    Field(strict=True, gt=0, le=MAX_SAFE_INTEGER),
]

AmountMinor = Annotated[
    int,
    Field(strict=True, ge=0, le=MAX_SAFE_INTEGER),
]

Month = Annotated[
    str,
    Field(
        strict=True,
        pattern=r"^[0-9]{4}-(0[1-9]|1[0-2])$",
    ),
]


class ContractModel(BaseModel):
    """Base común: rechaza campos que no pertenecen al contrato."""

    model_config = ConfigDict(extra="forbid")


class CategoryCoverage(ContractModel):
    """Identificador de categoría y comienzo de su cobertura."""

    category_id: PositiveId
    available_from_month: Month


class CategoryTotal(ContractModel):
    """Total de una categoría durante un mes, expresado en centavos."""

    category_id: PositiveId
    amount_minor: AmountMinor


class MonthlyHistory(ContractModel):
    """Totales por categoría de un mes completo."""

    month: Month
    category_totals: list[CategoryTotal] = Field(max_length=100)


class HistoryRequest(ContractModel):
    """Datos compartidos por analítica y predicción."""

    contract_version: Literal["1"]
    request_id: UUID
    organization_id: PositiveId
    currency: Literal["ARS"]
    categories: list[CategoryCoverage] = Field(max_length=100)
    history: list[MonthlyHistory] = Field(max_length=60)


class AnalyticsRequest(HistoryRequest):
    """Solicitud de comparación mensual."""

    pass


class PredictionRequest(HistoryRequest):
    """Solicitud de predicción del próximo mes con historial explícito."""

    target_month: Month


class InternalPredictionRequest(ContractModel):
    """Solicitud interna v2 donde Python consulta vistas SQL autorizadas."""

    contract_version: Literal["2"]
    request_id: UUID
    organization_id: PositiveId
    target_month: Month


class InternalAnalyticsRequest(ContractModel):
    """Solicitud interna v2 de analítica: Python consulta vistas SQL autorizadas."""

    contract_version: Literal["2"]
    request_id: UUID
    organization_id: PositiveId
    from_month: Month
    to_month: Month


class InternalOcrRequest(ContractModel):
    """Solicitud interna v1 para procesamiento OCR de un comprobante almacenado."""

    contract_version: Literal["1"]
    request_id: UUID
    organization_id: PositiveId
    receipt_id: UUID
    storage_key: Annotated[str, Field(min_length=1, max_length=500)]
