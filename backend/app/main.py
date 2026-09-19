from fastapi import FastAPI,HTTPException,Request,Header
import os
import secrets
from .analysis_service import ListingRequest,analyze_listing
from .collector import collect_all
from .engine import dedupe,match
from .models import BusinessDNA,Tender
from .llm import analyze
from .sources import SOURCES
from .store import init,upsert,list_tenders,get_tender,has_changes
app=FastAPI(title="BidSaarthi API",version="0.4.0");init()
@app.get("/health")
def health():return {"ok":True}
@app.get("/sources")
def sources():return [{"id":s.id,"name":s.name,"url":s.url,"mode":s.mode} for s in SOURCES]
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
