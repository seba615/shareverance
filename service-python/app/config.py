import os
from dataclasses import dataclass
from urllib.parse import quote_plus


@dataclass(frozen=True)
class Settings:
    predictor_mode: str
    database_url: str
    internal_service_token: str
    internal_service_token_previous: str
    receipts_storage_path: str = "/data/receipts"
    ocr_engine: str = "tesseract"


def _build_database_url() -> str:
    explicit_url = os.getenv("PYTHON_DATABASE_URL", "").strip()
    if explicit_url:
        return explicit_url

    host = os.getenv("PYTHON_DB_HOST", "postgres").strip()
    port = os.getenv("PYTHON_DB_PORT", "5432").strip()
    dbname = os.getenv("PYTHON_DB_NAME", os.getenv("POSTGRES_DB", "shareverance")).strip()
    user = os.getenv("PYTHON_DB_USER", "shareverance_python").strip()
    password = os.getenv("PYTHON_DB_PASSWORD", "")

    return (
        f"postgresql://{quote_plus(user)}:{quote_plus(password)}"
        f"@{host}:{port}/{quote_plus(dbname)}"
    )


def load_settings() -> Settings:
    mode = os.getenv("PREDICTOR_MODE", "mock").strip().lower()

    if mode not in ("mock", "ses"):
        raise ValueError(
            "PREDICTOR_MODE debe ser 'mock' (por defecto) o 'ses' (candidato experimental)."
        )

    return Settings(
        predictor_mode=mode,
        database_url=_build_database_url(),
        internal_service_token=os.getenv("PYTHON_CLIENT_SECRET", "").strip(),
        internal_service_token_previous=os.getenv(
            "PYTHON_CLIENT_SECRET_PREVIOUS", ""
        ).strip(),
        receipts_storage_path=os.getenv("RECEIPTS_STORAGE_PATH", "/data/receipts").strip(),
        ocr_engine=os.getenv("OCR_ENGINE", "tesseract").strip().lower(),
    )
