import json
import sqlite3
import threading
from pathlib import Path

DB = Path(__file__).resolve().parents[1] / "bidsaarthi.db"
lock = threading.Lock()

def init():
    with sqlite3.connect(DB) as c:
        c.execute(
            "create table if not exists tenders("
            "id text primary key, source_id text, source_url text, title text, "
            "closes_at text, content_hash text, payload text, "
            "updated_at text default current_timestamp)"
        )
        c.execute("create index if not exists idx_tenders_source on tenders(source_id)")
        c.execute(
            "create table if not exists tender_history("
            "id integer primary key autoincrement, tender_id text, content_hash text, "
            "closes_at text, payload text, created_at text default current_timestamp)"
        )

def upsert(items):
    with lock, sqlite3.connect(DB) as c:
        for t in items:
            old = c.execute(
                "select content_hash, closes_at, payload from tenders where id=?",
                (t.id,),
            ).fetchone()
            if old and old[0] != t.content_hash:
                c.execute(
                    "insert into tender_history(tender_id,content_hash,closes_at,payload) "
                    "values(?,?,?,?)",
                    (t.id, old[0], old[1], old[2]),
                )
            c.execute(
                "insert into tenders(id,source_id,source_url,title,closes_at,content_hash,payload) "
                "values(?,?,?,?,?,?,?) "
                "on conflict(id) do update set "
                "source_id=excluded.source_id, source_url=excluded.source_url, "
                "title=excluded.title, closes_at=excluded.closes_at, "
                "payload=excluded.payload, content_hash=excluded.content_hash, "
                "updated_at=current_timestamp",
                (
                    t.id, t.source_id, t.source_url, t.title,
                    t.closes_at, t.content_hash, t.model_dump_json(),
                ),
            )

def list_tenders(q="", limit=100):
    init()
    with sqlite3.connect(DB) as c:
        rows = c.execute(
            "select payload from tenders where title like ? "
            "order by updated_at desc limit ?",
            (f"%{q}%", limit),
        ).fetchall()
    return [json.loads(r[0]) for r in rows]

def get_tender(tender_id):
    init()
    with sqlite3.connect(DB) as c:
        row = c.execute(
            "select payload from tenders where id=?",
            (tender_id,),
        ).fetchone()
    return json.loads(row[0]) if row else None

def has_changes(tender_id):
    init()
    with sqlite3.connect(DB) as c:
        return c.execute(
            "select count(*) from tender_history where tender_id=?",
            (tender_id,),
        ).fetchone()[0] > 0


def tender_changes(tender_id,limit=20):
    init()
    with sqlite3.connect(DB) as c:
        rows=c.execute(
            "select content_hash,closes_at,payload,created_at from tender_history where tender_id=? order by id desc limit ?",
            (tender_id,max(1,min(limit,100))),
        ).fetchall()
    return [{"content_hash":r[0],"closes_at":r[1],"payload":json.loads(r[2]),"created_at":r[3]} for r in rows]

def search_tenders(q="",limit=50,offset=0,source_id=None,state=None):
    init(); limit=max(1,min(limit,100)); offset=max(0,offset)
    terms=f"%{q.strip()}%"
    sql="select payload from tenders where (title like ? or payload like ?)"
    args=[terms,terms]
    if source_id: sql+=" and source_id=?"; args.append(source_id)
    if state: sql+=" and payload like ?"; args.append(f"%{state}%")
    sql+=" order by updated_at desc limit ? offset ?"; args.extend([limit,offset])
    with sqlite3.connect(DB) as c: rows=c.execute(sql,args).fetchall()
    return [json.loads(r[0]) for r in rows]
