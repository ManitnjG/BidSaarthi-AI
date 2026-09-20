import asyncio
import httpx
from app.collector import parse,next_page,one
from app.sources import SOURCES


def source(id): return next(s for s in SOURCES if s.id==id)

def nic(title='Solar works',ref='REF/2026/1'):
    return f'<tr><td><a href="/nicgep/app?component=%24DirectLink&amp;sp=S1">{title}</a></td><td>{ref}</td><td>30-Sep-2026 05:00 PM</td><td>01-Oct-2026 11:00 AM</td></tr>'


def test_nic_homepage_ignores_corrigendum_and_keeps_separate_lots():
    html='<table id="activeTenders">'+nic('Solar work lot 1')+nic('Solar work lot 2')+'</table><table id="corrigendum">'+nic('Date extension')+'</table>'
    rows=parse(source('tn'),html)
    assert len(rows)==2
    assert rows[0].id != rows[1].id
    assert all(t.closes_at=='30-Sep-2026 05:00 PM' for t in rows)
    assert all(t.location=='Tamil Nadu' for t in rows)


def test_pagination_rejects_external_or_unrelated_urls():
    root=source('cppp').url
    assert next_page('<a href="https://evil.example/page2">Next</a>',root) is None
    assert next_page('<a href="/unrelated">Next</a>',root) is None
    assert next_page('<a href="?page=2">Next »</a>',root)==root+'?page=2'


def test_collector_returns_cursor_and_resumes_without_dropping_latest():
    root=source('cppp').url
    def handler(request):
        page=int(request.url.params.get('page','1'))
        html='<table>'+nic('Solar works page '+str(page),'REF'+str(page))+'</table>'
        if page<4: html+=f'<a href="?page={page+1}">Next »</a>'
        return httpx.Response(200,text=html)
    async def run():
        async with httpx.AsyncClient(transport=httpx.MockTransport(handler),trust_env=False) as c:
            first=await one(c,source('cppp'),max_pages=2)
            assert len(first['items'])==2
            assert first['next_url']==root+'?page=3'
            second=await one(c,source('cppp'),cursor=first['next_url'],max_pages=3)
            assert {t.reference_no for t in second['items']}=={'REF1','REF3','REF4'}
            assert second['next_url'] is None
    asyncio.run(run())


def test_registry_covers_36_regions_without_claiming_all_are_feeds():
    assert len({s.region for s in SOURCES if s.region!='India'})==36
    assert source('gem').mode.value=='LINK_ONLY'
    assert source('ireps').mode.value=='LINK_ONLY'
