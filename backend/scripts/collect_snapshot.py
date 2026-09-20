"""Accumulate public listings and resume pagination on the next scheduled run."""
import asyncio,json,sys
from datetime import datetime,timedelta
from pathlib import Path
from zoneinfo import ZoneInfo
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from app.collector import collect_all
from app.sources import SOURCES
ROOT=Path(__file__).resolve().parents[2]/'app/src/main/assets'
def read(name,default):
    try:return json.loads((ROOT/name).read_text())
    except (OSError,ValueError):return default

def retain(t):
    if not t.get('evidence',{}).get('listing'):return False
    for fmt in ('%d-%b-%Y %I:%M %p','%d-%b-%Y %H:%M'):
        try:
            end=datetime.strptime(t.get('closes_at') or '',fmt).replace(tzinfo=ZoneInfo('Asia/Kolkata'))
            return end>=datetime.now(ZoneInfo('Asia/Kolkata'))-timedelta(days=2)
        except ValueError:continue
    return True

def dedupe_rows(rows):
    rows=list(rows)
    def signature(row):return (row['source_id'],row.get('reference_no'),row.get('title'),row.get('department'))
    newer={signature(row) for row in rows if row.get('official_id')}
    unique={}
    for row in rows:
        sig=signature(row)
        if not row.get('official_id') and sig in newer:continue
        key=(row['source_id'],row['official_id'],row.get('department')) if row.get('official_id') else sig
        unique[key]=row
    return list(unique.values())

async def main():
    previous=read('tenders.json',[]);cursors=read('collection_cursors.json',{})
    results=await collect_all(cursors)
    rows={t['id']:t for t in previous if retain(t)}
    statuses=[]
    for result in results:
        for tender in result['items']:
            row=tender.model_dump()
            if retain(row):rows[row['id']]=row
        cursors[result['source']]=result.get('next_url')
        status={k:v for k,v in result.items() if k not in ('items','next_url')}
        status['count']=len(result['items'])
        status['stored_count']=sum(t['source_id']==result['source'] for t in rows.values())
        statuses.append(status)
    rows={row['id']:row for row in dedupe_rows(rows.values())}
    for status in statuses:
        status['stored_count']=sum(t['source_id']==status['source'] for t in rows.values())
    ROOT.mkdir(parents=True,exist_ok=True)
    for name,data in [('tenders.json',list(rows.values())),('source_status.json',statuses),('collection_cursors.json',cursors),
        ('sources.json',[{'id':s.id,'name':s.name,'url':s.url,'mode':s.mode.value,'region':s.region} for s in SOURCES])]:
        (ROOT/name).write_text(json.dumps(data,ensure_ascii=False),encoding='utf-8')
    print(json.dumps(statuses,ensure_ascii=False))
    if not rows:raise SystemExit('No verified listings available')
if __name__=='__main__':asyncio.run(main())
