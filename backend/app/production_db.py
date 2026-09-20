"""Production data model and migration bootstrap.

DATABASE_URL selects PostgreSQL in production. SQLite remains a local-development
fallback so existing installs are not destroyed during migration.
"""
import os

DATABASE_URL=os.getenv("DATABASE_URL","").strip()

POSTGRES_SCHEMA=[
"""create table if not exists users(id text primary key,email text unique not null,email_verified boolean default false,created_at timestamptz default now(),deleted_at timestamptz)""",
"""create table if not exists business_profiles(id text primary key,user_id text not null,profile jsonb not null,updated_at timestamptz default now())""",
"""create table if not exists tender_documents(id text primary key,tender_id text not null,url text not null,kind text,content_hash text,created_at timestamptz default now())""",
"""create table if not exists tender_versions(id bigserial primary key,tender_id text not null,content_hash text not null,payload jsonb not null,created_at timestamptz default now())""",
"""create table if not exists corrigenda(id text primary key,tender_id text not null,url text,payload jsonb,detected_at timestamptz default now())""",
"""create table if not exists saved_tenders(user_id text not null,tender_id text not null,created_at timestamptz default now(),primary key(user_id,tender_id))""",
"""create table if not exists analysis_results(id text primary key,user_id text,tender_id text not null,result jsonb not null,created_at timestamptz default now())""",
"""create table if not exists matches(user_id text not null,tender_id text not null,score integer not null,reasons jsonb,updated_at timestamptz default now(),primary key(user_id,tender_id))""",
"""create table if not exists alerts(id text primary key,user_id text not null,tender_id text,kind text not null,payload jsonb,status text default 'PENDING',created_at timestamptz default now())""",
"""create table if not exists subscriptions(user_id text primary key,provider text,product_id text,status text,expires_at timestamptz,updated_at timestamptz default now())""",
"""create table if not exists entitlements(user_id text primary key,plan text not null default 'FREE',features jsonb not null default '{}'::jsonb,updated_at timestamptz default now())""",
"""create table if not exists ai_usage(id bigserial primary key,user_id text,provider text,model text,tender_id text,created_at timestamptz default now())""",
"""create table if not exists source_health(source_id text primary key,status text,last_success timestamptz,last_error text,metadata jsonb,updated_at timestamptz default now())""",
"""create table if not exists audit_events(id bigserial primary key,user_id text,event text not null,metadata jsonb,created_at timestamptz default now())""",
]

def production_database_configured(): return DATABASE_URL.startswith("postgresql://") or DATABASE_URL.startswith("postgres://")
