"""ScrollShield Signature Sync API Server stub."""

import gzip
import json
from typing import Optional

from fastapi import FastAPI, Query, Response
from pydantic import BaseModel

app = FastAPI(title="ScrollShield Signature Sync API", version="0.1.0")


class AdSignature(BaseModel):
    id: str
    advertiser: str
    category: str
    caption_hash: str
    sim_hash: str
    confidence: float
    first_seen: str
    expires: str
    source: str
    locale: str


@app.get("/health")
def health():
    return {"status": "ok"}


@app.get("/api/v1/signatures")
def get_signatures(
    since: Optional[str] = Query(None, description="ISO timestamp filter"),
    locale: Optional[str] = Query(None, description="Locale filter"),
):
    signatures: list[dict] = []
    body = json.dumps(signatures).encode("utf-8")
    compressed = gzip.compress(body)
    return Response(
        content=compressed,
        media_type="application/json",
        headers={"Content-Encoding": "gzip"},
    )
