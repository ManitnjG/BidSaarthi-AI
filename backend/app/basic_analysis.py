"""Free deterministic checks. These are explicitly not generative AI."""
from datetime import datetime
from zoneinfo import ZoneInfo
from .models import Analysis, BusinessDNA


def basic_analysis(tender, business=None, reason=''):
    business = business or BusinessDNA()
    source = tender.evidence.get('listing', '')
    text = (tender.title + ' ' + tender.department + ' ' + source).lower()
    hits = [k for k in business.categories + business.keywords if k.strip() and k.lower() in text]
    reasons = ['Basic rule-based check; no AI model was used.']
    if hits:
        reasons.append('Matching business keywords: ' + ', '.join(dict.fromkeys(hits)))
    else:
        reasons.append('No matching business keywords found in the available listing.')
    if reason:
        reasons.append(reason)
    risks = ['Verify the full official tender, amendments, fees and exemptions before bidding.']
    for fmt in ('%d-%b-%Y %I:%M %p', '%d-%b-%Y %H:%M'):
        try:
            closes = datetime.strptime(tender.closes_at or '', fmt).replace(tzinfo=ZoneInfo('Asia/Kolkata'))
            hours = (closes - datetime.now(ZoneInfo('Asia/Kolkata'))).total_seconds() / 3600
            if hours < 0: risks.append('The listed submission deadline has passed.')
            elif hours <= 48: risks.append('The listed submission deadline is within 48 hours.')
            break
        except ValueError:
            continue
    return Analysis(tender_id=tender.id, analysis_mode='RULE_BASED', provider='on-device-compatible rules',
                    eligibility='UNKNOWN', summary=tender.title,
                    eligibility_reasons=reasons, risks=risks,
                    missing_documents=['Full eligibility clauses and document requirements need verification.'],
                    evidence={'listing': source[:600]} if source else {}, confidence=0)
