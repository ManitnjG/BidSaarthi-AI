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
