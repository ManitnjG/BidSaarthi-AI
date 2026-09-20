"""Public listing collector; preserves evidence and reports incomplete coverage."""
import asyncio
import hashlib
import os
import re
from datetime import datetime, timezone
from urllib.parse import urljoin, urlparse
import httpx
from bs4 import BeautifulSoup
from .models import Tender
from .sources import SOURCES, Mode

UA='Mozilla/5.0 (compatible; BidSaarthiAI/0.5; public tender discovery)'
DATE=re.compile(r'\b\d{1,2}-[A-Za-z]{3}-\d{4}\s+\d{1,2}:\d{2}\s+(?:AM|PM)\b',re.I)
def clean(s): return re.sub(r'\s+',' ',s or '').strip()
def _idx(headers,*names):
    return next((i for i,h in enumerate(headers) if any(n in h for n in names)),None)
def digest(value): return hashlib.sha256(value.encode()).hexdigest()

def parse(source,html):
    soup=BeautifulSoup(html,'html.parser')
    tables=soup.select('table#activeTenders') or soup.select('table')
    out=[]
    for table in tables:
        if 'corrig' in str(table.get('id','')).lower(): continue
        rows=[r for r in table.find_all('tr') if r.find_parent('table') is table]
        hdr=[]
        for r in rows:
            cells=r.find_all(['th','td'],recursive=False)
            text=[clean(c.get_text(' ',strip=True)).lower() for c in cells]
            if r.find('th') or ('list_header' in r.get('class',[])):
                hdr=text;break
        ti=_idx(hdr,'tender title','title of work','work description','tender description')
        ri=_idx(hdr,'reference no','tender ref','reference')
        ci=_idx(hdr,'closing date','bid submission end','closing')
        oi=_idx(hdr,'opening date','bid opening','opening')
        di=_idx(hdr,'organisation name','organization name','department')
        for row in rows:
            cells=row.find_all('td',recursive=False)
            if len(cells)<3 or any(c.find('table') for c in cells):continue
            vals=[clean(c.get_text(' ',strip=True)) for c in cells]
            if not any(DATE.search(v) for v in vals):continue
            anchor=next((a for c in cells for a in c.select('a[href]') if any(k in a['href'].lower() for k in ('directlink','tender','view'))),None)
            if anchor is None:continue
            href=urljoin(source.url,anchor['href'])
            if urlparse(href).scheme!='https' or urlparse(href).hostname!=urlparse(source.url).hostname:continue
            title=clean(anchor.get_text(' ',strip=True))
            title=re.sub(r'^\d+\.\s*','',title)
            ref=vals[ri] if ri is not None and ri<len(vals) else ''
            close_index=ci
            open_index=oi
            # NIC public homepage: title, reference, closing, opening.
            if len(cells)==4 and DATE.search(vals[2]) and DATE.search(vals[3]):
                ref=vals[1]; close_index=2; open_index=3
            else:
                cell=anchor.find_parent('td')
                raw=clean(cell.get_text(' ',strip=True))
                anchor_text=clean(anchor.get_text(' ',strip=True))
                suffix=raw[len(anchor_text):].lstrip(' /') if raw.startswith(anchor_text) else ''
                if '/' in suffix: ref=suffix.rsplit('/',1)[0].strip()
                elif not ref and len(cells)>=6 and anchor_text.count('/')>=2:
                    title,ref,_=anchor_text.rsplit('/',2)
                if close_index is None and len(cells)>=6:close_index=2
                if open_index is None and len(cells)>=6:open_index=3
            if ti is not None and ti<len(vals) and not title:title=vals[ti]
            if len(title)<4 or not ref or len(ref)<3:continue
            if close_index is None or close_index>=len(vals):continue
            closing=DATE.search(vals[close_index])
            if not closing:continue
            opening=DATE.search(vals[open_index]) if open_index is not None and open_index<len(vals) else None
            evidence=' | '.join(vals)[:4000]
            # One NIC reference may advertise several distinct lots. Do not collapse them.
            identity=f'{source.id}|{ref}' if source.id in ('cppp','state') else f'{source.id}|{ref}|{title}'
            department=vals[di] if di is not None and di<len(vals) else source.name
            out.append(Tender(id=digest(identity)[:24],source_id=source.id,source_url=href,title=title[:500],
                department=department,reference_no=ref,location=getattr(source,'region','India'),
                closes_at=closing.group(0),opens_at=opening.group(0) if opening else None,
                evidence={'listing':evidence},content_hash=digest(evidence),confidence=.95))
    return list({t.id:t for t in out}.values())

def next_page(html,url):
    soup=BeautifulSoup(html,'html.parser')
    for a in soup.select('a[href]'):
        label=clean(a.get_text(' ',strip=True)).lower()
        if 'next' not in label and 'next' not in a.get('rel',[]):continue
        dest=urljoin(url,a['href'])
        if urlparse(dest).scheme=='https' and urlparse(dest).hostname==urlparse(url).hostname and urlparse(dest).path==urlparse(url).path:
            return dest
    return None

def portal_total(html):
    text=BeautifulSoup(html,'html.parser').get_text(' ',strip=True)
    m=re.search(r'Total\s+Tenders\s*:\s*([\d,]+)',text,re.I)
    return int(m.group(1).replace(',','')) if m else None

async def one(client,s,cursor=None,max_pages=30):
    if s.mode==Mode.LINK_ONLY:return {'source':s.id,'status':'LINK_ONLY','items':[],'coverage':'Portal access only'}
    items={};seen=set();errors=[];url=s.url;pages=0;total=None;resume=None
    # Read latest page every run, then resume older pages from the last checkpoint.
    if cursor and (urlparse(cursor).hostname!=urlparse(s.url).hostname or urlparse(cursor).path!=urlparse(s.url).path):cursor=None
    while url and url not in seen and pages<max_pages:
        seen.add(url)
        try:
            r=await client.get(url,follow_redirects=True);r.raise_for_status()
            if urlparse(str(r.url)).hostname!=urlparse(s.url).hostname:raise ValueError('Unexpected portal redirect')
            rows=parse(s,r.text)
            pages+=1
            total=portal_total(r.text) or total
            if not rows:
                errors.append('No validated public tender rows; portal may require interactive access')
                resume=url if pages>1 else None
                break
            items.update({t.id:t for t in rows})
            nxt=next_page(r.text,str(r.url))
            if pages==1 and cursor and cursor!=s.url:nxt=cursor
            url=nxt;resume=nxt
            if url:await asyncio.sleep(.3)
        except Exception as e:
            errors.append(type(e).__name__+': '+str(e)[:160]);resume=url if pages else cursor;break
    partial=bool(resume) or s.id not in ('cppp','state') or bool(errors)
    return {'source':s.id,'status':('PARTIAL' if partial else 'LIVE') if items else 'UNAVAILABLE',
            'items':list(items.values()),'error':'; '.join(errors) or None,'pages':pages,
            'portal_total':total,'next_url':resume,'checked_at':datetime.now(timezone.utc).isoformat(),
            'coverage':'Public listings collected; full portal coverage not verified' if partial else 'Reached end of accessible listing pages'}

async def collect_all(cursors=None):
    cursors=cursors or {};sem=asyncio.Semaphore(5)
    max_pages=max(1,min(100,int(os.getenv('BIDSAARTHI_MAX_PAGES','30'))))
    async with httpx.AsyncClient(timeout=20,headers={'User-Agent':UA,'Accept':'text/html'},limits=httpx.Limits(max_connections=8)) as client:
        async def collect(s):
            async with sem:return await one(client,s,cursors.get(s.id),max_pages)
        return await asyncio.gather(*(collect(s) for s in SOURCES))
