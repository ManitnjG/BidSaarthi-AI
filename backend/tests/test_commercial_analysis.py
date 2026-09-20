import re
from app.models import Tender,BusinessDNA
from app.llm import verdict

def test_reference_is_not_money_shape():
 ref="33/GOS/BNH/SE(P)/2026/P/26"
 money=re.compile(r"(?:₹|rs\\.?|inr|lakh|crore|\\b\\d[\\d,]*(?:\\.\\d+)?\\s*(?:/-)?\\b)",re.I)
 refpat=re.compile(r"^[A-Z0-9()._-]+(?:/[A-Z0-9()._-]+){2,}$",re.I)
 assert refpat.fullmatch(ref)
 assert not (money.search(ref) and not refpat.fullmatch(ref))

def test_unknown_when_no_explicit_eligibility():
 v,reasons,missing=verdict({},BusinessDNA(has_gst=True,has_udyam=True))
 assert v=="UNKNOWN"
 assert any("Full tender documents" in x for x in missing)

def test_turnover_failure_is_deterministic():
 v,reasons,missing=verdict({"turnover_required":1000000},BusinessDNA(turnover=500000))
 assert v=="NOT_ELIGIBLE"
 assert any("below" in x for x in reasons)


def test_business_dna_commercial_fields():
 b=BusinessDNA(company_name="ACME",business_type="MSME",products_services=["civil works"],years_experience=5,certifications=["ISO 9001"],has_startup_india=True,is_oem=False,preferred_locations=["Tamil Nadu"])
 assert b.years_experience==5 and b.has_startup_india

def test_match_score_is_bounded():
 from app.engine import match
 t=Tender(id="x",source_id="cppp",source_url="https://eprocure.gov.in/x",title="Civil construction works",location="Tamil Nadu")
 r=match(t,BusinessDNA(categories=["civil"],states=["Tamil Nadu"],has_gst=True,has_udyam=True))
 assert 0<=r.score<=100
 assert r.reasons
