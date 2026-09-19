import asyncio, hashlib, re
from datetime import datetime, timezone
import httpx
from bs4 import BeautifulSoup
from .models import Tender
from .sources import SOURCES,Mode,Source

UA="BidSaarthiAI/0.2 (+public-tender-indexer; respects portal access controls)"

def clean(s:str)->str: return re.sub(r"\s+"," ",s).strip()

def parse_public_page(source:Source,html:str)->list[Tender]:
    soup=BeautifulSoup(html,"html.parser")
    rows=soup.select("tr")
    out=[]
    for i,row in enumerate(rows):
        cells=[clean(x.get_text(" ",strip=True)) for x in row.select("th,td")]
        text=" | ".join(cells)
        if len(text)<30: continue
        date=re.search(r"\b\d{2}[-/]\w{2,3}[-/]\d{4}(?:\s+\d{1,2}:\d{2}(?:\s*[AP]M)?)?",text,re.I)
        links=[a.get("href") for a in row.select("a[href]") if a.get("href")]
        if not date or not links: continue
        title=max(cells,key=len)[:300]
        key=f"{source.id}|{title}|{date.group(0)}"
        digest=hashlib.sha256(key.encode()).hexdigest()
        out.append(Tender(id=digest[:24],source_id=source.id,source_url=source.url,
          title=title,closes_at=date.group(0),content_hash=digest,
          evidence={"listing":text[:1000]},confidence=.72))
    return out[:100]

async def collect_source(client:httpx.AsyncClient,source:Source):
    if source.mode==Mode.LINK_ONLY: return {"source":source.id,"status":"LINK_ONLY","items":[]}
    try:
        r=await client.get(source.url,follow_redirects=True)
        r.raise_for_status()
        return {"source":source.id,"status":"LIVE","items":parse_public_page(source,r.text)}
    except Exception as e:
        return {"source":source.id,"status":"DEGRADED","error":str(e)[:180],"items":[]}

async def collect_all():
    limits=httpx.Limits(max_connections=4,max_keepalive_connections=2)
    async with httpx.AsyncClient(timeout=20,headers={"User-Agent":UA},limits=limits) as client:
        # conservative concurrency; no CAPTCHA/login/anti-bot bypass
        return await asyncio.gather(*(collect_source(client,s) for s in SOURCES))
