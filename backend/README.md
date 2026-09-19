# BidSaarthi Collector Backend
Run: `pip install -r requirements.txt && uvicorn app.main:app --reload`
Endpoints: /health, /sources, POST /collect, /tenders, POST /match.
Policy: official API/feed first, then permitted public HTML. Never bypass CAPTCHA, login, rate limits or anti-bot controls. Retain evidence/confidence and require manual verification for uncertain fields.
