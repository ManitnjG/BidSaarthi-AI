from app.collector import parse
from app.models import Analysis
from app.sources import SOURCES
HTML="""<table><tr><th>Tender Title</th><th>Reference No</th><th>Closing Date</th><th>Opening Date</th></tr><tr><td><a href="/tender/1">Solar rooftop installation</a></td><td>REF/001</td><td>26-Sep-2026 05:30 PM</td><td>27-Sep-2026 11:00 AM</td></tr><tr><td><a href="/tender/2">Electrical maintenance works</a></td><td>REF/002</td><td>28-Sep-2026 05:00 PM</td><td>29-Sep-2026 11:00 AM</td></tr></table>"""
def test_row_parser_no_title_corruption():
 rows=parse(SOURCES[0],HTML)
 assert [x.title for x in rows]==["Solar rooftop installation","Electrical maintenance works"]
 assert [x.reference_no for x in rows]==["REF/001","REF/002"]
 assert rows[0].closes_at=="26-Sep-2026 05:30 PM"
def test_analysis_schema():
 a=Analysis(tender_id="x",eligibility="UNKNOWN",opportunity_score=50,confidence=.5)
 assert a.opportunity_score==50
