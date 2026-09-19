import asyncio
import pytest
from fastapi.testclient import TestClient
from pydantic import ValidationError
from app.collector import parse
from app.sources import SOURCES
from app.llm import verdict
from app.models import BusinessDNA
from app.analysis_service import ListingRequest
from app.main import app


def test_deadline_change_preserves_identity():
    def html(date):
        return f'<table><tr><th>Tender Title</th><th>Reference No</th><th>Closing Date</th></tr><tr><td><a href="/tender/1">Solar rooftop installation</a></td><td>REF/001</td><td>{date}</td></tr></table>'
    before = parse(SOURCES[0], html('26-Sep-2026 05:30 PM'))[0]
    after = parse(SOURCES[0], html('29-Sep-2026 05:30 PM'))[0]
    assert before.id == after.id
    assert before.content_hash != after.content_hash


def test_listing_cannot_prove_full_eligibility():
    status, reasons, missing = verdict({'gst_required': True}, BusinessDNA(has_gst=True))
    assert status == 'UNKNOWN'
    assert missing


def test_negative_turnover_rejected():
    with pytest.raises(ValidationError):
        BusinessDNA(turnover=-1)


def test_unofficial_listing_rejected():
    with pytest.raises(ValidationError):
        ListingRequest(tender={'id':'x', 'title':'Work', 'source_id':'x', 'source_url':'https://gov.in.evil.example/notice'})


def test_collect_requires_server_token(monkeypatch):
    monkeypatch.delenv('BIDSAARTHI_COLLECTOR_TOKEN', raising=False)
    with TestClient(app) as c:
        assert c.post('/collect').status_code == 403


def test_unconfigured_ai_returns_honest_unknown(monkeypatch):
    monkeypatch.delenv('OPENAI_API_KEY', raising=False)
    monkeypatch.delenv('BIDSAARTHI_LLM_URL', raising=False)
    with TestClient(app) as c:
        response = c.post('/analyze-listing', json={'tender': {'id':'test-no-key', 'source_id':'cppp', 'source_url':'https://eprocure.gov.in/tender/1', 'title':'Supply solar panels'},'business':{}})
        assert response.status_code == 200
        assert response.json()['eligibility'] == 'UNKNOWN'
        assert response.json()['confidence'] == 0
