import os
from dataclasses import dataclass


@dataclass(frozen=True)
class Settings:
    predictor_mode: str


def load_settings() -> Settings:
    mode = os.getenv("PREDICTOR_MODE", "mock").strip().lower()

    if mode != "mock":
        raise ValueError(
            "PREDICTOR_MODE debe ser 'mock'. "
            "El predictor real todavía no está implementado."
        )

    return Settings(predictor_mode=mode)