from pydantic import BaseModel,Field
from typing import Optional,Literal

class Tender(BaseModel):
 id:str; official_id:str=""; source_id:str; source_url:str; title:str; department:str=""; reference_no:str=""; location:str=""; value:Optional[str]=None; emd:Optional[str]=None; tender_fee:Optional[str]=None; published_at:Optional[str]=None; closes_at:Optional[str]=None; opens_at:Optional[str]=None; document_urls:list[str]=Field(default_factory=list); confidence:float=1.0; evidence:dict[str,str]=Field(default_factory=dict); content_hash:str=""

class BusinessDNA(BaseModel):
 company_name:str=""; business_type:str=""; categories:list[str]=Field(default_factory=list); products_services:list[str]=Field(default_factory=list); states:list[str]=Field(default_factory=list); preferred_locations:list[str]=Field(default_factory=list); turnover:Optional[float]=Field(default=None,ge=0,allow_inf_nan=False); years_experience:Optional[float]=Field(default=None,ge=0); past_project_values:list[float]=Field(default_factory=list); certifications:list[str]=Field(default_factory=list); has_gst:bool=False; has_udyam:bool=False; has_startup_india:bool=False; is_oem:bool=False; keywords:list[str]=Field(default_factory=list); preferred_tender_min:Optional[float]=Field(default=None,ge=0); preferred_tender_max:Optional[float]=Field(default=None,ge=0)

class MatchResult(BaseModel):
 tender:Tender; score:int; reasons:list[str]; missing:list[str]

class Analysis(BaseModel):
 analysis_mode:Literal["AI","RULE_BASED"]="AI"; provider:str=""
 tender_id:str; structured:dict=Field(default_factory=dict); eligibility:Literal["ELIGIBLE","NOT_ELIGIBLE","UNKNOWN"]="UNKNOWN"; eligibility_reasons:list[str]=Field(default_factory=list); missing_documents:list[str]=Field(default_factory=list); opportunity_score:int=Field(default=0,ge=0,le=100); summary:str=""; risks:list[str]=Field(default_factory=list); evidence:dict[str,str]=Field(default_factory=dict); confidence:float=Field(default=0,ge=0,le=1)

class Entitlement(BaseModel):
 plan:Literal["FREE","PRO","BUSINESS"]="FREE"; active:bool=True; ai_monthly_limit:int=0; saved_limit:int=10; advanced_matching:bool=False; alerts:bool=False; exports:bool=False
