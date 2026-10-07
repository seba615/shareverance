from fastapi import FastAPI

app = FastAPI(title="Shareverance - Servicio Python", version="0.1.0")

@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "UP", "service": "python"}
