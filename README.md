# BidSaarthi AI

Native Android tender discovery and bid preparation app, with a FastAPI analysis backend.

## Version 0.4
- Search and filter official listings; sort by deadline and hide expired notices.
- Persistent business profiles, saved tender details, and preparation checklists.
- Source freshness and refresh failures shown explicitly. Bundled listings are not described as live.
- Listing analysis calls the backend; no generated match percentages or assumed registrations.
- Eligibility remains unknown until full tender documents are verified.

## Build
GitHub Actions builds a debug APK on pushes to main and publishes a GitHub release. Backend tests run separately. These are debug builds, not signed production Play Store releases.

## Backend
Install `backend/requirements.txt`, then run `uvicorn app.main:app --app-dir backend --host 0.0.0.0 --port 8000`.
Set `OPENAI_API_KEY` and `BIDSAARTHI_LLM_MODEL` to an available Responses API model, or configure `BIDSAARTHI_LLM_URL` and `BIDSAARTHI_LLM_KEY` for a compatible service. The Android app contains no provider key.
Deploy this backend revision before using the new `/analyze-listing` endpoint. Without a configured provider, the app reports that analysis is unavailable.
The optional `/collect` administrative endpoint requires `BIDSAARTHI_COLLECTOR_TOKEN` via `X-Collector-Token`. Scheduled GitHub collection works directly and does not require this token.

## Limits
Analysis currently reads listing text, not complete tender PDFs. GeM and Karnataka are portal links, not live feeds. Request limits and analysis caches are per process; a scaled public deployment needs shared rate limiting and authenticated quotas. Store backend SQLite data on persistent storage. Verify tender links, amendments and deadlines on the official portal before submitting a bid.

## Tests
`PYTHONPATH=backend pytest -q backend/tests`

## Free analysis
Free mode is enabled by default and does not use existing paid API keys. The app includes offline, rule-based keyword/deadline checks, clearly labelled as basic checks rather than AI.
For generative AI, add `OPENROUTER_API_KEY` in the backend environment. The default model is `openrouter/free`; `BIDSAARTHI_FREE_MODEL` may select another `:free` model. Free providers have quotas and availability limits; failures fall back to basic checks. No provider key is embedded in the APK.
Paid providers require an explicit `BIDSAARTHI_ALLOW_PAID_AI=true` setting.
Official provider documentation: https://openrouter.ai/docs/cookbook/get-started/free-models-router-playground
