from dataclasses import dataclass
from enum import Enum
class Mode(str,Enum): HTML="HTML"; API="API"; LINK_ONLY="LINK_ONLY"
@dataclass(frozen=True)
class Source: id:str; name:str; url:str; mode:Mode
SOURCES=[
 Source("cppp","CPPP / Central eProcurement","https://eprocure.gov.in/eprocure/app",Mode.HTML),
 Source("tn","Tamil Nadu eProcurement","https://tntenders.gov.in/nicgep/app",Mode.HTML),
 Source("gem","Government e-Marketplace","https://gem.gov.in",Mode.LINK_ONLY),
 Source("maha","Maharashtra eTender","https://mahatenders.gov.in/nicgep/app",Mode.HTML),
 Source("kerala","Kerala eTenders","https://etenders.kerala.gov.in/nicgep/app",Mode.HTML),
 Source("karnataka","Karnataka Public Procurement","https://kppp.karnataka.gov.in",Mode.LINK_ONLY)]
