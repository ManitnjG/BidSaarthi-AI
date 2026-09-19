import json,os,re,httpx
from .models import Analysis,Tender,BusinessDNA
class LLMClient:
 async def extract(self,t):raise NotImplementedError
class ResponsesClient(LLMClient):
 def __init__(self,url,key):self.url=url;self.key=key
 async def extract(self,t):
  prompt="""Extract tender facts only from SOURCE. Never invent missing facts.
Return JSON with: value, emd, fee, category, state, turnover_required,
experience_required, gst_required, udyam_required, eligibility_notes,
required_documents, risks, summary, evidence. evidence maps populated fields
to short source quotes. SOURCE:
"""+t.evidence.get("listing","")
  async with httpx.AsyncClient(timeout=45) as c:
   r=await c.post(self.url,headers={"Authorization":"Bearer "+self.key,"Content-Type":"application/json"},json={"model":os.getenv("BIDSAARTHI_LLM_MODEL","gpt-5.6-luna"),"input":prompt,"text":{"format":{"type":"json_object"}}})
   r.raise_for_status();d=r.json()
  text=d.get("output_text")
  if not text:
   text=next((x.get("text") for o in d.get("output",[]) for x in o.get("content",[]) if x.get("type")=="output_text"),None)
  if not text:raise ValueError("no JSON output")
  return json.loads(text)
def client():
 if os.getenv("BIDSAARTHI_LLM_URL") and os.getenv("BIDSAARTHI_LLM_KEY"):return ResponsesClient(os.environ["BIDSAARTHI_LLM_URL"],os.environ["BIDSAARTHI_LLM_KEY"])
 if os.getenv("OPENAI_API_KEY"):return ResponsesClient("https://api.openai.com/v1/responses",os.environ["OPENAI_API_KEY"])
 return None
def verdict(s,b):
 reasons=[];missing=[];failed=[]
 if s.get("gst_required") is True:(reasons if b.has_gst else failed).append("GST requirement "+("met" if b.has_gst else "not met"))
 if s.get("udyam_required") is True:(reasons if b.has_udyam else failed).append("Udyam requirement "+("met" if b.has_udyam else "not met"))
 if s.get("turnover_required"):missing.append("Turnover threshold needs verification against extracted clause")
 if s.get("experience_required"):missing.append("Experience evidence needs manual verification")
 return ("NOT_ELIGIBLE" if failed else "UNKNOWN" if missing else "ELIGIBLE"),reasons+failed,missing
async def analyze(t,b=None):
 b=b or BusinessDNA();c=client()
 if not c:return Analysis(tender_id=t.id,eligibility_reasons=["LLM unavailable; rule-based fallback active"],missing_documents=["Manual tender-document review"],summary=t.title,risks=["Structured eligibility unavailable"],evidence=t.evidence,confidence=0)
 try:
  s=await c.extract(t);v,reasons,unknown=verdict(s,b);docs=list(dict.fromkeys(s.get("required_documents",[])+unknown));score=70 if v=="ELIGIBLE" else 25 if v=="NOT_ELIGIBLE" else 50
  return Analysis(tender_id=t.id,structured=s,eligibility=v,eligibility_reasons=reasons,missing_documents=docs,opportunity_score=score,summary=s.get("summary") or t.title,risks=s.get("risks",[]),evidence=s.get("evidence",{}),confidence=.85)
 except Exception:
  return Analysis(tender_id=t.id,eligibility_reasons=["AI analysis unavailable; fallback active"],missing_documents=["Manual tender-document review"],summary=t.title,risks=["AI extraction failed"],evidence=t.evidence,confidence=0)
