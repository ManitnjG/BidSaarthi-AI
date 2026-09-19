# BidSaarthi AI v0.2
Official portals -> collector -> evidence/confidence -> dedupe -> database/API -> Business DNA matcher -> Android.

Implemented: (1) collector backend and source health, (2) evidence/confidence extraction foundation, (3) normalization/deduplication, (4) FastAPI persistence/search API using SQLite for development, (5) explainable Business DNA matching, (6) modular Android backend-ready client structure.

Production should use managed Postgres, authenticated scheduled workers, server-side secrets, document object storage, OCR and source-specific parsers. Never place privileged credentials in the APK.
