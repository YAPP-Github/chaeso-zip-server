create table channel_recommendations (
    id                     uuid        primary key default gen_random_uuid(),
    user_id                uuid        not null references users (id),
    onboarding_id          uuid        not null references onboarding_responses (id),
    channel_id             uuid        not null references channels (id),
    rank                   integer     not null,   -- 추천 순위
    score                  integer     not null,   -- 적합도(%)
    reason                 varchar(500) not null,  -- 추천 근거 한 줄
    reason_tags            text[],                 -- 근거가 된 매칭 축 (CATEGORY/OBJECTIVE/AGE_BAND)

    -- 아래는 모두 추천 시점 값의 복사본
    channel_name           varchar(255) not null,
    est_pricing_model      varchar(20),
    est_unit_price         numeric,
    est_impressions_min    bigint,
    est_impressions_max    bigint,
    est_clicks_min         bigint,
    est_clicks_max         bigint,
    cpc_won                numeric,
    pricing_models_all     text[],                 -- 그 채널이 그 시점에 가지고 있던 과금 방식 전체
    min_budget_won_snap    bigint,
    audience_summary_snap  varchar(255),           -- 추천이 만든 주요 타깃 문구
    is_executable          boolean     not null,
    shortfall_won          bigint,

    created_at             timestamp   not null default now(),

    constraint uq_channel_recommendation_onboarding_channel unique (onboarding_id, channel_id)
);

create index idx_channel_recommendation_user_created
    on channel_recommendations (user_id, created_at desc);

create index idx_channel_recommendation_onboarding
    on channel_recommendations (onboarding_id, rank);
