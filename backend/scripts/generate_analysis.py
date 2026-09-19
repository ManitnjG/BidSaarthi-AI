import asyncio,json,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from app.models import Tender
from app.llm import analyze

ROOT=Path(__file__).resolve().parents[2]
TENDERS=ROOT/"app/src/main/assets/tenders.json"
OUT=ROOT/"app/src/main/assets/analysis.json"

async def main():
 rows=json.loads(TENDERS.read_text(encoding="utf-8"))
 results={}
 # Keep GitHub Actions/API usage bounded; newest collected public listings first.
 for raw in rows[:40]:
  t=Tender(**raw)
  a=await analyze(t)
  results[t.id]=a.model_dump()
 OUT.write_text(json.dumps(results,ensure_ascii=False),encoding="utf-8")
 print("AI analyses:",len(results))

if __name__=="__main__":
 asyncio.run(main())
