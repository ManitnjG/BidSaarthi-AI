from pydantic import BaseModel, Field
from typing import Optional

class Tender(BaseModel):
    id: str
    source_id: str
    source_url: str
    title: str
    department: str = ""
    reference_no: str = ""
    location: str = ""
    value: Optional[str] = None
    emd: Optional[str] = None
    tender_fee: Optional[str] = None
    published_at: Optional[str] = None
    closes_at: Optional[str] = None
    opens_at: Optional[str] = None
    document_urls: list[str] = Field(default_factory=list)
    confidence: float = 1.0
    evidence: dict[str,str] = Field(default_factory=dict)
    content_hash: str = ""

class BusinessDNA(BaseModel):
    categories:list[str]=Field(default_factory=list)
    states:list[str]=Field(default_factory=list)
    turnover:Optional[float]=None
    has_gst:bool=False
    has_udyam:bool=False
    keywords:list[str]=Field(default_factory=list)

class MatchResult(BaseModel):
    tender:Tender
    score:int
    reasons:list[str]
    missing:list[str]
