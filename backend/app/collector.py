import asyncio,hashlib,re,httpx
from bs4 import BeautifulSoup
from urllib.parse import urljoin
from .models import Tender
from .sources import SOURCES,Mode

UA="Mozilla/5.0 (compatible; BidSaarthiAI/0.3; +https://github.com/ManitnjG/BidSaarthi-AI)"
DATE=re.compile(r"\b\d{2}-[A-Za-z]{3}-\d{4}\s+\d{1,2}:\d{2}\s+(?:AM|PM)\b",re.I)

def clean(s): return re.sub(r"\s+"," ",s or "").strip()

def parse(source,html):
 out=[]
 soup=BeautifulSoup(html,"html.parser")
 for row in soup.select("tr"):
  cells=row.select("td")
  if len(cells)<3: continue
  vals=[clean(x.get_text(" ",strip=True)) for x in cells]
  dates=[m.group(0) for v in vals for m in DATE.finditer(v)]
  if not dates: continue
  links=[a for a in row.select("a[href]") if len(clean(a.get_text(" ",strip=True)))>=8]
  title_link=max(links,key=lambda a:len(clean(a.get_text(" ",strip=True))),default=None)
  title=clean(title_link.get_text(" ",strip=True)) if title_link else ""
  if len(title)<8:
   candidates=[v for v in vals if len(v)>=8 and not DATE.search(v) and not re.fullmatch(r"\d+\.?",v)]
   title=max(candidates,key=len,default="")
  if len(title)<8: continue
  href=urljoin(source.url,title_link.get("href")) if title_link else source.url
  ref=next((v for v in vals if v!=title and not DATE.search(v) and 4<=len(v)<=160 and not re.fullmatch(r"\d+\.?",v)),"")
  closes=dates[-1]
  digest=hashlib.sha256(f"{source.id}|{title}|{ref}|{closes}".encode()).hexdigest()
  out.append(Tender(id=digest[:24],source_id=source.id,source_url=href,title=title[:400],
    department=source.name,reference_no=ref,location="Tamil Nadu" if source.id=="tn" else "India",
    closes_at=closes,content_hash=digest,evidence={"listing":" | ".join(vals)[:1500]},confidence=.9))
 return list({x.id:x for x in out}.values())[:100]

async def one(client,s):
 if s.mode==Mode.LINK_ONLY:return {"source":s.id,"status":"LINK_ONLY","items":[]}
 try:
  r=await client.get(s.url,follow_redirects=True);r.raise_for_status()
  items=parse(s,r.text)
  return {"source":s.id,"status":"LIVE" if items else "EMPTY","items":items}
 except Exception as e:return {"source":s.id,"status":"DEGRADED","error":str(e)[:180],"items":[]}

async def collect_all():
 async with httpx.AsyncClient(timeout=30,headers={"User-Agent":UA,"Accept":"text/html,application/xhtml+xml"},limits=httpx.Limits(max_connections=4)) as c:
  return await asyncio.gather(*(one(c,s) for s in SOURCES))
