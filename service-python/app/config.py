import os
from dataclasses import dataclass


@dataclass(frozen=True)
class Settings:
    predictor_mode: str
    java_service_url: str
    java_client_id: str
    java_client_secret: str


def load_settings() -> Settings:
    mode = os.getenv("PREDICTOR_MODE", "mock").strip().lower()

    if mode != "mock":
        raise ValueError(
            "PREDICTOR_MODE debe ser 'mock'. "
            "El predictor real todavía no está implementado."
        )

    return Settings(
        predictor_mode=mode,
        java_service_url=os.getenv("JAVA_SERVICE_URL", "http://java-service:8080").rstrip("/"),
        java_client_id=os.getenv("JAVA_CLIENT_ID", "python-prediction-service").strip(),
        java_client_secret=os.getenv("JAVA_CLIENT_SECRET", ""),
    )