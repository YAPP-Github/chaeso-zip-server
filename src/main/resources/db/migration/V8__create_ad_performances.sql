create unique index uq_onboarding_active_user
    on onboarding_responses (user_id) where is_active;

create table ad_performances (
    id                    uuid        primary key default gen_random_uuid(),
    user_id               uuid        not null references users (id),
    source_type           varchar(20) not null default 'MANUAL',
    channel_id            uuid        references channels (id),
    channel_product_id    uuid        references channel_products (id),
    external_channel_name varchar(255),
    started_at            date,
    ended_at              date,
    budget_won            bigint,
    impressions           bigint,
    clicks                bigint,
    conversions           bigint,
    ctr_actual            numeric,
    cpc_actual            numeric,
    cpa_actual            numeric,
    raw_file_url          varchar(1000),
    created_at            timestamp   not null default now(),
    updated_at            timestamp,
    constraint ck_perf_channel_identified
        check (channel_id is not null or external_channel_name is not null)
);

create index idx_perf_user    on ad_performances (user_id);
create index idx_perf_channel on ad_performances (channel_id);
