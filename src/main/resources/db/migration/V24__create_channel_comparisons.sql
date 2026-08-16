create table channel_comparisons (
    id             uuid        primary key default gen_random_uuid(),
    user_id        uuid        not null references users (id),
    onboarding_id  uuid        references onboarding_responses (id),
    service_name   varchar(255),                  -- onboarding_id 없을 때만 채움
    created_at     timestamp   not null default now(),
    constraint ck_channel_comparisons_source
        check ((onboarding_id is not null and service_name is null)
            or (onboarding_id is null and nullif(btrim(service_name), '') is not null))
);

create index idx_channel_comparison_user_created
    on channel_comparisons (user_id, created_at desc);

create table channel_comparison_items (
    id                       uuid         primary key default gen_random_uuid(),
    comparison_id            uuid         not null references channel_comparisons (id) on delete cascade,
    channel_id               uuid         not null references channels (id),
    sort_order               integer      not null,   -- 온보딩 있으면 적합도순, 없으면 요청 순서
    match_rate               integer,                 -- 적합도(%) 스냅샷. 온보딩 없으면 null
    tags_snap                text[],                  -- 채널 인사이트 태그 스냅샷(최대 2개)

    -- 아래는 모두 비교 시점 값의 복사본
    channel_name             varchar(255) not null,
    preview_image_url_snap   varchar(500),
    display_platforms_snap   text[],
    advantages_snap          text[],
    audience_summary_snap    varchar(255),
    ad_formats_snap          text[],
    targeting_methods_snap   text[],
    execution_type_snap      varchar(20),
    pricing_models_all       text[],
    cpc_won                  numeric,
    cpm_won                  numeric,
    min_budget_won_snap      integer,
    est_impressions_min      bigint,
    est_impressions_max      bigint,
    est_clicks_min           bigint,
    est_clicks_max           bigint,

    created_at               timestamp    not null default now()
);

create index idx_channel_comparison_item_comparison
    on channel_comparison_items (comparison_id, sort_order);
