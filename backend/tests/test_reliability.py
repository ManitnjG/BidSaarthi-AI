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


def test_cppp_closing_date_is_not_opening_date():
    html = '''<table><tr><td>1</td><td>20-Sep-2026 10:00 AM</td><td>26-Sep-2026 05:30 PM</td><td>27-Sep-2026 11:00 AM</td><td><a href="/cppp/tendersfullview/1">Solar installation/REF001/TENDER001</a></td><td>Central Department</td></tr></table>'''
    row = parse(SOURCES[0], html)[0]
    assert row.closes_at == '26-Sep-2026 05:30 PM'


def test_cppp_slashes_preserved_in_reference():
    html = '''<table><tr><th>Sl.No</th><th>e-Published Date</th><th>Bid Submission Closing Date</th><th>Tender Opening Date</th><th>Title/Ref.No./Tender Id</th><th>Organisation Name</th></tr><tr><td>1</td><td>20-Sep-2026 10:00 AM</td><td>26-Sep-2026 05:30 PM</td><td>27-Sep-2026 11:00 AM</td><td><a href="/cppp/tendersfullview/1">Solar installation</a>/EE/Kanpur/2026-27/169614</td><td>Public Works Department</td></tr></table>'''
    row = parse(SOURCES[0], html)[0]
    assert row.title == 'Solar installation'
    assert row.reference_no == 'EE/Kanpur/2026-27'
    assert row.department == 'Public Works Department'


def test_paid_keys_are_not_used_in_free_mode(monkeypatch):
    from app.llm import client
    monkeypatch.delenv('OPENROUTER_API_KEY', raising=False)
    monkeypatch.delenv('BIDSAARTHI_ALLOW_PAID_AI', raising=False)
    monkeypatch.setenv('OPENAI_API_KEY', 'test-not-a-real-key')
    assert client() is None


def test_free_router_rejects_paid_model(monkeypatch):
    from app.llm import OpenRouterFreeClient
    from app.models import Tender
    monkeypatch.setenv('BIDSAARTHI_FREE_MODEL', 'paid/model')
    tender = Tender(id='x',source_id='cppp',source_url='https://eprocure.gov.in/tender/1',title='Solar',evidence={'listing':'Solar'})
    with pytest.raises(ValueError, match='Only free'):
        asyncio.run(OpenRouterFreeClient('test-not-a-real-key').extract(tender))


def test_free_fallback_never_claims_ai_or_eligibility():
    from app.basic_analysis import basic_analysis
    from app.models import Tender
    tender = Tender(id='x',source_id='cppp',source_url='https://eprocure.gov.in/tender/1',title='Solar work',closes_at='01-Jan-2020 10:00 AM',evidence={'listing':'Solar work'})
    result = basic_analysis(tender,BusinessDNA(categories=['Solar']))
    assert result.analysis_mode == 'RULE_BASED'
    assert result.eligibility == 'UNKNOWN'
    assert result.opportunity_score == 0
    assert any('Solar' in s for s in result.eligibility_reasons)
    assert any('passed' in s for s in result.risks)


def test_free_router_schema_and_evidence(monkeypatch):
    import json, httpx
    from app.llm import OpenRouterFreeClient
    from app.models import Tender
    real_client = httpx.AsyncClient
    def handle(request):
        payload = json.loads(request.content)
        assert payload['model'] == 'openrouter/free'
        assert payload['response_format']['type'] == 'json_schema'
        return httpx.Response(200,json={'choices':[{'message':{'content':json.dumps({'summary':'Solar work','gst_required':True,'required_documents':['Invented document'],'evidence':{'gst_required':'Invented GST requirement','summary':'Solar work'}})}}]})
    monkeypatch.delenv('BIDSAARTHI_FREE_MODEL', raising=False)
    monkeypatch.setattr(httpx,'AsyncClient',lambda **kw: real_client(transport=httpx.MockTransport(handle),trust_env=False,**kw))
    tender = Tender(id='x',source_id='cppp',source_url='https://eprocure.gov.in/tender/1',title='Solar work',evidence={'listing':'Solar work'})
    result = asyncio.run(OpenRouterFreeClient('test-not-a-real-key').extract(tender))
    assert result['gst_required'] is None
    assert result['required_documents'] == []
    assert result['evidence'] == {'summary':'Solar work'}
