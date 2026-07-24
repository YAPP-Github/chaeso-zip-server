create table onboarding_ad_history_snapshots (
    id                uuid         primary key default gen_random_uuid(),
    onboarding_id     uuid         not null references onboarding_responses (id),
    channel_id        uuid         references channels (id),
    channel_name_snap varchar(255) not null,
    budget_won_snap   bigint,
    impressions_snap  bigint,
    clicks_snap       bigint,
    conversions_snap  bigint,
    started_at_snap   date,
    ended_at_snap     date,
    raw_file_url_snap varchar(1000),
    created_at        timestamp    not null default now()
);

create index idx_onboarding_ad_history_snapshot_onboarding
    on onboarding_ad_history_snapshots (onboarding_id);
