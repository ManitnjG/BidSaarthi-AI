from dataclasses import asdict,dataclass,field
from datetime import datetime
from typing import Optional
@dataclass
class Tender:
 source:str;tender_id:str;title:str;organisation:str;url:str;reference_no:str="";published_at:Optional[str]=None;bid_close_at:Optional[str]=None;state:Optional[str]=None;corrigendum:Optional[str]=None;fetched_at:Optional[str]=None
 @property
 def key(self):return f"{self.source}:{self.tender_id}"
 def to_dict(self):return asdict(self)
 def is_open(self,now=None):
  if not self.bid_close_at:return True
  now=now or datetime.now().astimezone();return datetime.fromisoformat(self.bid_close_at)>now
@dataclass
class FetchResult:
 source:str;tenders:list[Tender]=field(default_factory=list);pages_fetched:int=0;rows_skipped:int=0;stopped_reason:str=""
class ConnectorError(Exception):pass
class ParseError(ConnectorError):pass
class BlockedError(ConnectorError):pass
