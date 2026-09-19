"""Conservative CPPP central/state active-tender connector. Public metadata only."""
import os,re,time,urllib.request,urllib.robotparser
from datetime import datetime
from html.parser import HTMLParser
from typing import Optional
from urllib.parse import urljoin,urlparse
from .base import *
BASE="https://eprocure.gov.in";LIST_URL=BASE+"/cppp/latestactivetendersnew/cpppdata";STATES_LIST_URL=BASE+"/cppp/latestactivetendersnew/mmpdata"
def ua():return "BidSaarthi-AI/0.2 (+https://github.com/ManitnjG/BidSaarthi-AI; "+os.getenv("BIDSAARTHI_CONTACT","contact-not-configured")+")"
def dt(s):
 for f in ("%d-%b-%Y %I:%M %p","%d-%b-%Y %H:%M"):
  try:return datetime.strptime(s.strip(),f).astimezone().isoformat()
  except ValueError:pass
 return None
class P(HTMLParser):
 def __init__(self):super().__init__();self.rows=[];self.row=None;self.cell=None;self.href=None
 def handle_starttag(self,t,a):
  d=dict(a)
  if t=="tr":self.row=[]
  elif t in ("td","th") and self.row is not None:self.cell=["",[]]
  elif t=="a" and self.cell is not None:self.href=d.get("href");self.cell[1].append(self.href) if self.href else None
 def handle_data(self,d):
  if self.cell is not None:self.cell[0]+=d
 def handle_endtag(self,t):
  if t in ("td","th") and self.cell is not None:self.row.append(self.cell);self.cell=None
  elif t=="tr" and self.row is not None:self.rows.append(self.row);self.row=None
def parse_listing(html,base_url=LIST_URL,fetched_at=None,source="cppp"):
 p=P();p.feed(html);out=[];skip=0;next_url=None
 for m in re.finditer(r'href=["\']([^"\']+)["\'][^>]*>\s*(?:Next|Next\s*&raquo;)',html,re.I):next_url=urljoin(base_url,m.group(1));break
 for row in p.rows:
  texts=[re.sub(r"\s+"," ",c[0]).strip() for c in row]
  joined=" | ".join(texts)
  if "Tender ID" in joined or len(texts)<3:continue
  link=next((h for c in row for h in c[1] if h and ("tendersfullview" in h)),None)
  if not link:continue
  titlecell=next((c for c in row if link in c[1]),None);raw=re.sub(r"\s+"," ",titlecell[0]).strip() if titlecell else ""
  parts=raw.rsplit("/",2)
  if len(parts)<3:skip+=1;continue
  title,ref,tid=parts
  dates=[dt(x) for x in texts if dt(x)]
  if not tid.strip():skip+=1;continue
  out.append(Tender(source=source,tender_id=tid.strip(),title=title.strip(),organisation=texts[0] if texts else "",url=urljoin(base_url,link),reference_no=ref.strip(),published_at=dates[0] if dates else None,bid_close_at=dates[-1] if dates else None,state=(texts[0] if source=="cppp_states" else None),fetched_at=fetched_at or datetime.now().astimezone().isoformat()))
 if not out:raise ParseError("no tender rows with expected detail links were found")
 return out,skip,next_url
class CpppConnector:
 def __init__(self,url=LIST_URL,source="cppp",delay=1.0,fetch=None):self.url=url;self.source=source;self.delay=delay;self.fetcher=fetch
 def _get(self,url):
  if self.fetcher:return self.fetcher(url)
  req=urllib.request.Request(url,headers={"User-Agent":ua()});return urllib.request.urlopen(req,timeout=30).read().decode("utf-8","replace")
 def fetch(self,max_pages=5,stop_before=None):
  r=FetchResult(self.source);url=self.url;seen=set()
  for n in range(max_pages):
   if url in seen or urlparse(url).netloc!="eprocure.gov.in":break
   seen.add(url);html=self._get(url)
   if "Access Denied" in html or "Request Rejected" in html:raise BlockedError("portal blocked automated request")
   ts,sk,nxt=parse_listing(html,url,source=self.source);r.pages_fetched+=1;r.rows_skipped+=sk
   for t in ts:
    if t.key not in {x.key for x in r.tenders}:r.tenders.append(t)
   url=nxt
   if not url:break
   if self.delay:time.sleep(self.delay)
  return r
class CpppStatesConnector(CpppConnector):
 def __init__(self,**kw):super().__init__(url=STATES_LIST_URL,source="cppp_states",**kw)
