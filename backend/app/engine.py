import re
from .models import MatchResult
def key(t): return re.sub(r"[^a-z0-9]","",(t.reference_no or "").lower()) or re.sub(r"[^a-z0-9]","",f"{t.department}{t.title}{t.closes_at}".lower())[:180]
def dedupe(items):
 best={}
 for t in items:
  k=key(t)
  if k not in best or t.confidence>best[k].confidence:best[k]=t
 return list(best.values())
def match(t,b):
 hay=f"{t.title} {t.department} {t.location}".lower();score=0;reasons=[];missing=[];hit=[k for k in [*b.categories,*b.keywords] if k.lower() in hay]\n profile=set(re.findall(r"[a-z0-9]+"," ".join([*b.categories,*b.keywords]).lower()));words=set(re.findall(r"[a-z0-9]+",hay));semantic=(len(profile & words)/len(profile)) if profile else 0\n if semantic:score+=min(30,round(semantic*30));reasons.append(f"Business-profile relevance {round(semantic*100)}%")
 if hit:score+=25;reasons.append("Category/keyword match: "+", ".join(hit[:4]))
 if b.states and any(s.lower() in hay for s in b.states):score+=20;reasons.append("Location match")
 if b.has_gst:score+=10
 else:missing.append("GST status not confirmed")
 if b.has_udyam:score+=10
 else:missing.append("Udyam/MSME status not confirmed")
 if t.confidence<.8:missing.append("Manual verification of extracted fields")
 return MatchResult(tender=t,score=min(score,100),reasons=reasons,missing=missing)
