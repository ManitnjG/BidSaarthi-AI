import re
from .models import Tender,BusinessDNA,MatchResult

def normalize_key(t:Tender):
    ref=re.sub(r"[^a-z0-9]","",(t.reference_no or "").lower())
    title=re.sub(r"[^a-z0-9]","",t.title.lower())[:120]
    return ref or f"{t.department.lower()}:{title}:{t.closes_at or ''}"

def dedupe(items:list[Tender]):
    best={}
    for t in items:
        k=normalize_key(t)
        if k not in best or t.confidence>best[k].confidence: best[k]=t
    return list(best.values())

def match(t:Tender,b:BusinessDNA)->MatchResult:
    hay=f"{t.title} {t.department} {t.location}".lower()
    score=0; reasons=[]; missing=[]
    keys=[*b.categories,*b.keywords]
    hit=[k for k in keys if k.lower() in hay]
    if hit: score+=55; reasons.append("Category/keyword match: "+", ".join(hit[:4]))
    if b.states and any(s.lower() in hay for s in b.states): score+=20; reasons.append("Location match")
    if b.has_gst: score+=10
    else: missing.append("GST status not confirmed")
    if b.has_udyam: score+=10
    else: missing.append("Udyam/MSME status not confirmed")
    if t.confidence<.8: missing.append("Manual verification of extracted fields")
    return MatchResult(tender=t,score=min(score,100),reasons=reasons,missing=missing)
