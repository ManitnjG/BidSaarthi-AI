"""Bounded listing analysis; mobile callers never need a provider API key."""
import asyncio
import hashlib
import os
import time
from collections import OrderedDict, deque
from urllib.parse import urlparse
from fastapi import HTTPException, Request
from pydantic import BaseModel, Field, field_validator
from .models import Tender, BusinessDNA
from .llm import analyze

class ListingRequest(BaseModel):
    tender: Tender
    business: BusinessDNA = Field(default_factory=BusinessDNA)

    @field_validator('tender')
    @classmethod
    def official_listing(cls, tender):
        url = urlparse(tender.source_url)
        host = (url.hostname or '').lower()
        if url.scheme != 'https' or not (host.endswith('.gov.in') or host.endswith('.nic.in')):
            raise ValueError('An HTTPS official government notice URL is required')
        if len(tender.model_dump_json()) > 16000:
            raise ValueError('Listing is too large')
        return tender

_cache = OrderedDict()
_requests = OrderedDict()
_slots = asyncio.Semaphore(3)

def check_rate(request: Request):
    ip = request.client.host if request.client else 'unknown'
    now = time.monotonic()
    bucket = _requests.setdefault(ip, deque())
    while bucket and bucket[0] < now - 60:
        bucket.popleft()
    if len(bucket) >= 10:
        raise HTTPException(429, 'Please wait before requesting more analysis')
    bucket.append(now)
    _requests.move_to_end(ip)
    while len(_requests) > 4096:
        _requests.popitem(last=False)

async def analyze_listing(payload: ListingRequest, request: Request):
    check_rate(request)
    if len(payload.business.model_dump_json()) > 4000:
        raise HTTPException(413, 'Business profile is too large')
    key = hashlib.sha256(payload.model_dump_json().encode()).hexdigest()
    cached = _cache.get(key)
    if cached and cached[0] > time.monotonic():
        return cached[1]
    if _slots.locked():
        raise HTTPException(429, 'Analysis is busy. Try again shortly.')
    async with _slots:
        result = await analyze(payload.tender, payload.business)
    if result.confidence > 0:
        _cache[key] = (time.monotonic() + 900, result)
        while len(_cache) > 256:
            _cache.popitem(last=False)
    return result
