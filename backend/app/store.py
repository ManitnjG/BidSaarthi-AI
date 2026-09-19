import json,sqlite3,threading
from pathlib import Path
DB=Path(__file__).resolve().parents[1]/"bidsaarthi.db"; lock=threading.Lock()
def init():
 with sqlite3.connect(DB) as c:
  c.execute("create table if not exists tenders(id text primary key,source_id text,source_url text,title text,closes_at text,content_hash text,payload text,updated_at text default current_timestamp)")
  c.execute("create index if not exists idx_tenders_source on tenders(source_id)")\n  c.execute("create table if not exists tender_history(id integer primary key autoincrement,tender_id text,content_hash text,closes_at text,payload text,created_at text default current_timestamp)")
def upsert(items):
 with lock,sqlite3.connect(DB) as c:
  for t in items:\n   old=c.execute("select content_hash,closes_at,payload from tenders where id=?",(t.id,)).fetchone()\n   if old and old[0]!=t.content_hash:c.execute("insert into tender_history(tender_id,content_hash,closes_at,payload) values(?,?,?,?)",(t.id,old[0],old[1],old[2]))\n   c.execute("insert into tenders(id,source_id,source_url,title,closes_at,content_hash,payload) values(?,?,?,?,?,?,?) on conflict(id) do update set payload=excluded.payload,content_hash=excluded.content_hash,updated_at=current_timestamp",(t.id,t.source_id,t.source_url,t.title,t.closes_at,t.content_hash,t.model_dump_json()))
def list_tenders(q="",limit=100):
 init()
 with sqlite3.connect(DB) as c:
  rows=c.execute("select payload from tenders where title like ? order by updated_at desc limit ?",(f"%{q}%",limit)).fetchall()
 return [json.loads(r[0]) for r in rows]

def get_tender(tender_id):
 init()
 with sqlite3.connect(DB) as c:
  r=c.execute("select payload from tenders where id=?",(tender_id,)).fetchone()
 return json.loads(r[0]) if r else None

def has_changes(tender_id):\n init()\n with sqlite3.connect(DB) as c:return c.execute("select count(*) from tender_history where tender_id=?",(tender_id,)).fetchone()[0]>0\n