from pydantic import BaseModel,Field
from typing import Optional,Literal
class Tender(BaseModel):
 id:str; source_id:str; source_url:str; title:str; department:str=""; reference_no:str=""; location:str=""; value:Optional[str]=None; emd:Optional[str]=None; tender_fee:Optional[str]=None; published_at:Optional[str]=None; closes_at:Optional[str]=None; opens_at:Optional[str]=None; document_urls:list[str]=Field(default_factory=list); confidence:float=1.0; evidence:dict[str,str]=Field(default_factory=dict); content_hash:str=""
class BusinessDNA(BaseModel):
 categories:list[str]=Field(default_factory=list); states:list[str]=Field(default_factory=list); turnover:Optional[float]=Field(default=None,ge=0,allow_inf_nan=False); has_gst:bool=False; has_udyam:bool=False; keywords:list[str]=Field(default_factory=list)
class MatchResult(BaseModel):
 tender:Tender; score:int; reasons:list[str]; missing:list[str]
class Analysis(BaseModel):
 tender_id:str; structured:dict=Field(default_factory=dict); eligibility:Literal["ELIGIBLE","NOT_ELIGIBLE","UNKNOWN"]="UNKNOWN"; eligibility_reasons:list[str]=Field(default_factory=list); missing_documents:list[str]=Field(default_factory=list); opportunity_score:int=Field(default=0,ge=0,le=100); summary:str=""; risks:list[str]=Field(default_factory=list); evidence:dict[str,str]=Field(default_factory=dict); confidence:float=Field(default=0,ge=0,le=1)
