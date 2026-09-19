from fastapi import FastAPI
from .collector import collect_all
from .engine import dedupe,match
from .models import BusinessDNA,Tender
from .sources import SOURCES
from .store import init,upsert,list_tenders
app=FastAPI(title="BidSaarthi Collector API",version="0.2.0");init()
@app.get("/health")
def health():return {"ok":True}
@app.get("/sources")
def sources():return [{"id":s.id,"name":s.name,"url":s.url,"mode":s.mode} for s in SOURCES]
@app.post("/collect")
async def collect():
 results=await collect_all();items=dedupe([t for r in results for t in r["items"]]);upsert(items);return {"sources":[{"source":r["source"],"status":r["status"],"count":len(r["items"])} for r in results],"stored":len(items)}
@app.get("/tenders")
def tenders(q:str="",limit:int=100):return list_tenders(q,min(limit,200))
@app.post("/match")
def matches(b:BusinessDNA,limit:int=100):return sorted([match(Tender(**x),b) for x in list_tenders(limit=limit)],key=lambda x:x.score,reverse=True)
