from dataclasses import dataclass
from enum import Enum

class Mode(str, Enum):
    HTML='HTML'
    API='API'
    LINK_ONLY='LINK_ONLY'

@dataclass(frozen=True)
class Source:
    id: str
    name: str
    url: str
    mode: Mode
    region: str = 'India'

# Public NIC portals. Non-public/API-only portals remain explicitly link-only.
NIC_PORTALS = [
 ('tn','Tamil Nadu','tntenders.gov.in'), ('maha','Maharashtra','mahatenders.gov.in'),
 ('kerala','Kerala','etenders.kerala.gov.in'), ('wb','West Bengal','wbtenders.gov.in'),
 ('up','Uttar Pradesh','etender.up.nic.in'), ('haryana','Haryana','etenders.hry.nic.in'),
 ('rajasthan','Rajasthan','eproc.rajasthan.gov.in'), ('mp','Madhya Pradesh','mptenders.gov.in'),
 ('odisha','Odisha','tendersodisha.gov.in'), ('assam','Assam','assamtenders.gov.in'),
 ('jharkhand','Jharkhand','jharkhandtenders.gov.in'), ('hp','Himachal Pradesh','hptenders.gov.in'),
 ('jk','Jammu and Kashmir','jktenders.gov.in'), ('punjab','Punjab','eproc.punjab.gov.in'),
 ('goa','Goa','eprocure.goa.gov.in'), ('tripura','Tripura','tripuratenders.gov.in'),
 ('manipur','Manipur','manipurtenders.gov.in'), ('meghalaya','Meghalaya','meghalayatenders.gov.in'),
 ('nagaland','Nagaland','nagalandtenders.gov.in'), ('arunachal','Arunachal Pradesh','arunachaltenders.gov.in'),
 ('mizoram','Mizoram','mizoramtenders.gov.in'), ('sikkim','Sikkim','sikkimtender.gov.in'),
 ('uttarakhand','Uttarakhand','uktenders.gov.in'), ('delhi','Delhi','govtprocurement.delhi.gov.in'),
 ('puducherry','Puducherry','pudutenders.gov.in'), ('chandigarh','Chandigarh','etenders.chd.nic.in'),
 ('andaman','Andaman and Nicobar Islands','eprocure.andaman.gov.in'),
 ('ladakh','Ladakh','tenders.ladakh.gov.in'), ('lakshadweep','Lakshadweep','tendersutl.gov.in'),
 ('dnh','Dadra and Nagar Haveli and Daman and Diu','dnhtenders.gov.in'),
]
SOURCES = [
 Source('state','State eProcurement (MMP)','https://eprocure.gov.in/cppp/latestactivetendersnew/mmpdata',Mode.HTML),
 Source('cppp','CPPP / Central eProcurement','https://eprocure.gov.in/cppp/latestactivetendersnew/cpppdata',Mode.HTML),
 *[Source(i,n+' eProcurement','https://'+host+'/nicgep/app',Mode.HTML,n) for i,n,host in NIC_PORTALS],
 Source('gem','GeM','https://bidplus.gem.gov.in/all-bids',Mode.LINK_ONLY),
 Source('karnataka','Karnataka procurement','https://kppp.karnataka.gov.in',Mode.LINK_ONLY,'Karnataka'),
 Source('gujarat','Gujarat nProcure','https://tender.nprocure.com',Mode.LINK_ONLY,'Gujarat'),
 Source('ap','Andhra Pradesh eProcurement','https://tender.apeprocurement.gov.in',Mode.LINK_ONLY,'Andhra Pradesh'),
 Source('telangana','Telangana eProcurement','https://tender.telangana.gov.in',Mode.LINK_ONLY,'Telangana'),
 Source('bihar','Bihar eProcurement','https://eproc2.bihar.gov.in',Mode.LINK_ONLY,'Bihar'),
 Source('cg','Chhattisgarh eProcurement','https://eproc.cgstate.gov.in',Mode.LINK_ONLY,'Chhattisgarh'),
 Source('ireps','Indian Railways IREPS','https://www.ireps.gov.in',Mode.LINK_ONLY),
 Source('wb_new','West Bengal new procurement portal','https://tenders.wb.gov.in',Mode.LINK_ONLY,'West Bengal'),
]
