# BidSaarthi Production Readiness

BidSaarthi is an independent tender discovery and analysis product. It is not affiliated with the Government of India or any issuing authority. Official tender notices, documents and corrigenda are authoritative.

## Current audit

| Area | Status | Production gate |
|---|---|---|
| Android native/Compose | WORKING | Continue API-first migration and release signing |
| Official tender parsing | WORKING/PARTIAL | Measure source coverage and parser health continuously |
| Exact official links | PARTIAL | Preserve detail/document URLs; label portal-only links honestly |
| OpenRouter AI + deterministic fallback | WORKING/PARTIAL | Production provider health, quotas and evidence regression tests |
| Business DNA | PARTIAL | Expanded schema added; account-backed persistence remains |
| Hybrid matching | PARTIAL | Explainable lexical/profile matching exists; semantic/vector layer remains |
| Corrigendum/change history | PARTIAL | History exists; user-specific delivery remains |
| Versioned API | PARTIAL | v1 foundation added; authenticated private resources remain |
| Authentication | MISSING | Required before commercial accounts |
| Paid entitlements/billing | MISSING | Never enable paid access until server-side receipt verification exists |
| PostgreSQL/migrations | MISSING | Required before multi-instance production scale |
| Secure document uploads | MISSING | Requires authenticated object storage, validation and retention policy |
| Admin/observability | PARTIAL | Add protected operations endpoints and external error monitoring |
| Privacy/Terms/account deletion | MISSING | Required before public paid launch |

## Commercial release gates

1. PostgreSQL production database and reversible migrations.
2. Verified user identity, email verification, reset and deletion flows.
3. Server-authoritative FREE/PRO/BUSINESS entitlements.
4. Google Play subscription receipt verification and webhook/reconciliation path.
5. AI usage accounting, per-plan quotas and abuse controls.
6. User-specific saved tenders, profiles, alerts and audit events.
7. Secure document storage with MIME/size validation and malware scanning integration.
8. Privacy Policy, Terms and data-retention/deletion documentation reviewed for the launch jurisdiction.
9. Release signing, Play Console configuration, crash/error monitoring and backup/restore test.
10. Acceptance suite proving real tender -> API -> Android -> evidence-grounded analysis -> fallback -> change alert.

Do not label the product PRODUCTION READY until every release gate is demonstrated against the deployed production environment.
