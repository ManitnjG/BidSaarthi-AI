import asyncio,hashlib,re,httpx
from bs4 import BeautifulSoup
from .models import Tender
from .sources import SOURCES,Mode
UA="BidSaarthiAI/0.2 public-tender-indexer"
def clean(s): return re.sub(r"\s+"," ",s).strip()
def parse(source,html):
 out=[]
 for row in BeautifulSoup(html,"html.parser").select("tr"):
  cells=[clean(x.get_text(" ",strip=True)) for x in row.select("th,td")]; text=" | ".join(cells)
  date=re.search(r"\b\d{2}[-/]\w{2,3}[-/]\d{4}(?:\s+\d{1,2}:\d{2}(?:\s*[AP]M)?)?",text,re.I)
  if len(text)<30 or not date or not row.select("a[href]"): continue
  title=max(cells,key=len)[:300]; digest=hashlib.sha256(f"{source.id}|{title}|{date.group(0)}".encode()).hexdigest()
  out.append(Tender(id=digest[:24],source_id=source.id,source_url=source.url,title=title,closes_at=date.group(0),content_hash=digest,evidence={"listing":text[:1000]},confidence=.72))
 return out[:100]
async def one(client,s):
 if s.mode==Mode.LINK_ONLY:return {"source":s.id,"status":"LINK_ONLY","items":[]}
 try:
  r=await client.get(s.url,follow_redirects=True);r.raise_for_status();return {"source":s.id,"status":"LIVE","items":parse(s,r.text)}
 except Exception as e:return {"source":s.id,"status":"DEGRADED","error":str(e)[:180],"items":[]}
async def collect_all():
 async with httpx.AsyncClient(timeout=20,headers={"User-Agent":UA},limits=httpx.Limits(max_connections=4)) as c:return await asyncio.gather(*(one(c,s) for s in SOURCES))
