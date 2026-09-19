import json, sqlite3, threading
from pathlib import Path
from .models import Tender
DB=Path(__file__).resolve().parents[1]/"bidsaarthi.db"
_lock=threading.Lock()

def init():
    with sqlite3.connect(DB) as c:
        c.execute("""create table if not exists tenders(
          id text primary key, source_id text, source_url text, title text,
          closes_at text, content_hash text, payload text, updated_at text default current_timestamp)""")
        c.execute("create index if not exists idx_tenders_source on tenders(source_id)")
        c.execute("create index if not exists idx_tenders_close on tenders(closes_at)")

def upsert(items:list[Tender]):
    with _lock, sqlite3.connect(DB) as c:
        for t in items:
            c.execute("""insert into tenders(id,source_id,source_url,title,closes_at,content_hash,payload)
             values(?,?,?,?,?,?,?) on conflict(id) do update set payload=excluded.payload,
             content_hash=excluded.content_hash,updated_at=current_timestamp""",
             (t.id,t.source_id,t.source_url,t.title,t.closes_at,t.content_hash,t.model_dump_json()))

def list_tenders(q:str="",limit:int=100):
    init()
    with sqlite3.connect(DB) as c:
        if q:
            rows=c.execute("select payload from tenders where title like ? order by updated_at desc limit ?",(f"%{q}%",limit)).fetchall()
        else: rows=c.execute("select payload from tenders order by updated_at desc limit ?",(limit,)).fetchall()
    return [json.loads(r[0]) for r in rows]
