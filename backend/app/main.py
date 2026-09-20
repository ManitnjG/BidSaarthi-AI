from fastapi import FastAPI,HTTPException,Request,Header
import os
import secrets
from .analysis_service import ListingRequest,analyze_listing
from .collector import collect_all
from .engine import dedupe,match
from .models import BusinessDNA,Tender,Entitlement
from .llm import analyze
from .sources import SOURCES
from .store import init,upsert,list_tenders,get_tender,has_changes
from .production_db import production_database_configured
app=FastAPI(title="BidSaarthi API",version="1.0.0",description="Evidence-first Indian tender intelligence API. Official issuing authorities remain authoritative.");init()
@app.get("/health")
def health():return {"ok":True}
@app.get("/sources")
def sources():return [{"id":s.id,"name":s.name,"url":s.url,"mode":s.mode,"region":s.region} for s in SOURCES]
@app.post("/collect")
async def collect(x_collector_token: str | None = Header(default=None)):
 expected=os.getenv("BIDSAARTHI_COLLECTOR_TOKEN", "")
 if not expected or not secrets.compare_digest(x_collector_token or "",expected):
  raise HTTPException(403,"Collection requires the server collector token")
 results=await collect_all();items=dedupe([t for r in results for t in r["items"]]);upsert(items)
 return {"sources":[{"source":r["source"],"status":r["status"],"count":len(r["items"]),"error":r.get("error")} for r in results],"stored":len(items)}
@app.get("/tenders")
def tenders(q:str="",limit:int=100):return list_tenders(q,min(limit,200))
@app.post("/match")
def matches(b:BusinessDNA,limit:int=100):return sorted([match(Tender(**x),b) for x in list_tenders(limit=limit)],key=lambda x:x.score,reverse=True)

@app.post("/analyze/{tender_id}")
async def analysis(tender_id:str,request:Request,business:BusinessDNA|None=None):
 raw=get_tender(tender_id)
 if not raw:raise HTTPException(404,"Tender not found")
 return await analyze_listing(ListingRequest(tender=Tender(**raw),business=business or BusinessDNA()),request)

@app.post("/alerts")
def alerts(limit:int=200):
 from datetime import datetime,timedelta
 out=[]
 for raw in list_tenders(limit=limit):
  t=Tender(**raw);flags=[]
  if has_changes(t.id):flags.append("CORRIGENDUM_OR_LISTING_CHANGED")
  if not t.document_urls:flags.append("DOCUMENTS_NOT_CAPTURED")
  if t.closes_at:
   for fmt in ("%d-%b-%Y %I:%M %p","%d-%b-%Y %H:%M"):
    try:
     d=datetime.strptime(t.closes_at,fmt)
     if datetime.now()<=d<=datetime.now()+timedelta(hours=48):flags.append("DEADLINE_48H")
     break
    except ValueError:pass
  if flags:out.append({"tender_id":t.id,"title":t.title,"alerts":flags,"evidence":t.evidence})
 return out

@app.post("/analyze-listing")
async def listing_analysis(payload:ListingRequest,request:Request):
 return await analyze_listing(payload,request)


# Versioned production API. Legacy routes remain during the Android migration.
@app.get("/api/v1/health")
def v1_health():
 return {"ok":True,"api_version":"v1","service":"BidSaarthi","government_affiliation":False}

@app.get("/api/v1/tenders")
def v1_tenders(q:str="",limit:int=50,offset:int=0,source_id:str|None=None,state:str|None=None):
 limit=max(1,min(limit,100));offset=max(0,offset)
 rows=list_tenders(q, min(offset+limit,1000))
 if source_id: rows=[x for x in rows if x.get("source_id")==source_id]
 if state: rows=[x for x in rows if state.lower() in (x.get("location") or "").lower()]
 page=rows[offset:offset+limit]
 return {"items":page,"count":len(page),"offset":offset,"limit":limit,"disclaimer":"Verify all requirements and amendments with the issuing authority."}

@app.get("/api/v1/tenders/{tender_id}")
def v1_tender(tender_id:str):
 raw=get_tender(tender_id)
 if not raw: raise HTTPException(404,"Tender not found")
 return raw

@app.post("/api/v1/matches")
def v1_matches(b:BusinessDNA,limit:int=50):
 rows=[match(Tender(**x),b) for x in list_tenders(limit=min(max(limit,1),100))]
 return {"items":sorted(rows,key=lambda x:x.score,reverse=True),"eligibility_note":"Match score is relevance, not proof of tender eligibility."}

@app.get("/api/v1/subscription")
def v1_subscription():
 # Server-authoritative default until a verified billing identity is attached.
 return Entitlement()

@app.get("/api/v1/usage")
def v1_usage():
 return {"ai_used":0,"ai_limit":0,"plan":"FREE","note":"Authenticated usage accounting is required before paid entitlements are enabled."}

@app.post("/api/v1/tenders/{tender_id}/analyze")
async def v1_analysis(tender_id:str,request:Request,business:BusinessDNA|None=None):
 raw=get_tender(tender_id)
 if not raw: raise HTTPException(404,"Tender not found")
 return await analyze_listing(ListingRequest(tender=Tender(**raw),business=business or BusinessDNA()),request)


@app.get("/api/v1/readiness")
def readiness():
 return {
  "service":"BidSaarthi",
  "api":"v1",
  "database":{"postgresql_configured":production_database_configured()},
  "ai":{"server_side":True,"openrouter_configured":bool(os.getenv("OPENROUTER_API_KEY"))},
  "billing":{"server_verified":False,"commercial_unlock_enabled":False},
  "disclaimer":"Not affiliated with any government. Official issuing-authority documents are authoritative."
 }

@app.get("/api/v1/tenders/{tender_id}/changes")
def v1_changes(tender_id:str):
 if not get_tender(tender_id): raise HTTPException(404,"Tender not found")
 return {"tender_id":tender_id,"changed":has_changes(tender_id)}

@app.get("/api/v1/tenders/{tender_id}/documents")
def v1_documents(tender_id:str):
 raw=get_tender(tender_id)
 if not raw: raise HTTPException(404,"Tender not found")
 return {"items":raw.get("document_urls",[]),"note":"Only publicly captured official document URLs are returned; protected documents are not bypassed."}
