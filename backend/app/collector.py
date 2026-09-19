import asyncio,hashlib,re,httpx
from bs4 import BeautifulSoup
from urllib.parse import urljoin
from .models import Tender
from .sources import SOURCES,Mode
UA="Mozilla/5.0 (compatible; BidSaarthiAI/0.4; +https://github.com/ManitnjG/BidSaarthi-AI)"
DATE=re.compile(r"\b\d{1,2}-[A-Za-z]{3}-\d{4}\s+\d{1,2}:\d{2}\s+(?:AM|PM)\b",re.I)
def clean(s):return re.sub(r"\s+"," ",s or "").strip()
def _idx(headers,*names):
 for i,h in enumerate(headers):
  if any(n in h for n in names):return i
 return None
def parse(source,html):
 soup=BeautifulSoup(html,"html.parser");out=[]
 for table in soup.select("table"):
  rows=table.select("tr");hdr=[]
  for r in rows:
   hs=r.select("th")
   if hs:hdr=[clean(x.get_text(" ",strip=True)).lower() for x in hs];break
  ti=_idx(hdr,"tender title","title of work","work description","tender description")
  ri=_idx(hdr,"reference no","tender ref","tender id","reference")
  ci=_idx(hdr,"closing date","bid submission end","close date","closing")
  oi=_idx(hdr,"opening date","bid opening","open date","opening")
  for row in rows:
   cells=row.select("td")
   if len(cells)<3:continue
   vals=[clean(c.get_text(" ",strip=True)) for c in cells]
   dates=[m.group(0) for v in vals for m in DATE.finditer(v)]
   if not dates:continue
   detail_cell=None;detail_link=None
   for cell in cells:
    for a in cell.select("a[href]"):
     href=a.get("href","").lower();txt=clean(a.get_text(" ",strip=True))
     if "directlink" in href or "tender" in href or ("view" in href and len(txt)>3):
      detail_cell=cell;detail_link=a;break
    if detail_cell:break
   title=vals[ti] if ti is not None and ti<len(vals) else ""
   ref=vals[ri] if ri is not None and ri<len(vals) else ""
   if detail_cell is not None:
    raw=clean(detail_cell.get_text(" ",strip=True));parts=[clean(x) for x in raw.rsplit("/",2)]
    if len(parts)==3:
     if not title:title=parts[0]
     if not ref:ref=parts[1]
    if not title:title=clean(detail_link.get_text(" ",strip=True)) if detail_link else raw
   if not title:
    candidates=[v for v in vals if len(v)>=8 and not DATE.search(v) and not re.fullmatch(r"\d+\.?",v)]
    title=candidates[-1] if candidates else ""
   if len(title)<8:continue
   closes=DATE.search(vals[ci]).group(0) if ci is not None and ci<len(vals) and DATE.search(vals[ci]) else dates[-1]
   opens=DATE.search(vals[oi]).group(0) if oi is not None and oi<len(vals) and DATE.search(vals[oi]) else None
   if not ref:
    candidates=[v for v in vals if v!=title and not DATE.search(v) and 4<=len(v)<=120 and not any(x in v.lower() for x in ("mis reports","tenders by","downloads","site compatibility"))]
    ref=candidates[-1] if candidates else ""
   href=urljoin(source.url,detail_link.get("href")) if detail_link else source.url
   evidence=" | ".join(vals)[:4000];digest=hashlib.sha256(f"{source.id}|{title}|{ref}|{closes}".encode()).hexdigest()
   out.append(Tender(id=digest[:24],source_id=source.id,source_url=href,title=title[:500],department=source.name,reference_no=ref,location="Tamil Nadu" if source.id=="tn" else "India",closes_at=closes,opens_at=opens,content_hash=digest,evidence={"listing":evidence},confidence=.95))
 return list({(x.source_id,x.reference_no or x.id):x for x in out}.values())[:100]
async def one(client,s):
 if s.mode==Mode.LINK_ONLY:return {"source":s.id,"status":"LINK_ONLY","items":[]}
 try:
  r=await client.get(s.url,follow_redirects=True);r.raise_for_status();items=parse(s,r.text)
  return {"source":s.id,"status":"LIVE" if items else "EMPTY","items":items}
 except Exception as e:return {"source":s.id,"status":"DEGRADED","error":str(e)[:180],"items":[]}
async def collect_all():
 async with httpx.AsyncClient(timeout=30,headers={"User-Agent":UA,"Accept":"text/html,application/xhtml+xml"},limits=httpx.Limits(max_connections=4)) as c:return await asyncio.gather(*(one(c,s) for s in SOURCES))
