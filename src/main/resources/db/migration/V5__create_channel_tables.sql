-- ─────────────────────────── 온보딩 ───────────────────────────
create table onboarding_responses (
                                      id                 uuid         primary key default gen_random_uuid(),
                                      user_id            uuid         not null references users(id),
                                      service_name       varchar(255),
                                      industry           varchar(30),
                                      service_type       varchar(20),
                                      campaign_objective varchar(20),
                                      budget_min         integer,
                                      budget_max         integer,
                                      period             varchar(20),
                                      ad_experience      varchar(20),
                                      target_age_bands   text[],
                                      is_active          boolean      not null default true,
                                      created_at         timestamp    not null default now(),
                                      updated_at         timestamp
);
create index idx_onboarding_user on onboarding_responses (user_id);


-- ─────────────────────────── 추출 프로버넌스 ───────────────────────────
create table source_documents (
                                  id              uuid         primary key default gen_random_uuid(),
                                  source_path     varchar(1000) not null unique,
                                  file_name       varchar(500) not null,
                                  company_name    varchar(255),
                                  service_folder  varchar(255),
                                  file_size_bytes bigint,
                                  source_format   varchar(20)  not null,
                                  extract_method  varchar(20),
                                  page_count      integer,
                                  char_count      integer,
                                  is_image_pdf    boolean,
                                  is_media_kit    boolean      not null default true,
                                  priority        varchar(20),
                                  valid_period    varchar(100),
                                  data_quality    varchar(20),
                                  extracted_at    timestamp,
                                  created_at      timestamp    not null default now(),
                                  updated_at      timestamp
);


-- ─────────────────────────── 채널 카탈로그 ───────────────────────────
create table channels (
                          id                    uuid         primary key default gen_random_uuid(),
                          name                  varchar(255) not null,
                          display_platforms     text[],
                          logo_url              varchar(500),
                          media_type            varchar(20),
                          primary_category      varchar(30),   -- 매체 대표 업종 1개
                          suitable_categories   text[],
                          default_tags          text[],
                          advantages            text[],
                          preview_image_url     varchar(500),
                          audience_summary      varchar(255),
                          primary_age_band      varchar(50),
                          age_band_codes        text[],       -- 온보딩 연령 매칭용 코드배열 (10s/20s/30s/40s/50s_plus)
                          primary_gender        varchar(20),
                          audience_traits       text,
                          ad_formats            text[],
                          targeting_methods     text[],
                          execution_type        varchar(20),
                          min_budget_won        integer,
                          max_budget_won        integer,
                          avg_daily_impressions bigint,
                          description           text,
                          recommendation_basis  text,
                          is_active             boolean      not null default true,
                          source_document_id    uuid         references source_documents(id),
                          created_at            timestamp    not null default now(),
                          updated_at            timestamp
);
create index idx_channels_active on channels (is_active);


-- 광고 상품 (채널 1:N 상품)
create table channel_products (
                                  id                   uuid         primary key default gen_random_uuid(),
                                  channel_id           uuid         not null references channels(id),
                                  product_name         varchar(255),
                                  inventory_type       varchar(100),
                                  supported_objectives text[],
                                  min_budget_won       integer,
                                  max_budget_won       integer,
                                  ctr                  numeric,
                                  ctr_min              numeric,      -- 예상 클릭율 하한(%) (범위 명시 시)
                                  ctr_max              numeric,      -- 예상 클릭율 상한(%)
                                  expected_impressions bigint,
                                  expected_period      varchar(50),
                                  source_document_id   uuid         references source_documents(id),
                                  created_at           timestamp    not null default now(),
                                  updated_at           timestamp
);
create index idx_products_channel on channel_products (channel_id);


-- 단가 (상품 1:N 가격)
create table channel_pricing (
                                 id                 uuid         primary key default gen_random_uuid(),
                                 channel_product_id uuid         not null references channel_products(id),
                                 pricing_model      varchar(20)  not null,
                                 value              numeric,
                                 value_max          numeric,
                                 currency           varchar(10)  not null default 'KRW',
                                 unit_period        varchar(50),
                                 unit_days          numeric,      -- 계산용 단위 일수(일=1/주=7/월=30, 기간무관이면 null)
                                 price_type         varchar(20)  not null default 'UNKNOWN',
                                 vat                varchar(20)  not null default 'UNKNOWN',
                                 segment            varchar(100),
                                 valid_period       varchar(100),
                                 raw_text           text,
                                 source_document_id uuid         references source_documents(id),
                                 verified           boolean      not null default false,
                                 created_at         timestamp    not null default now(),
                                 updated_at         timestamp
);
create index idx_pricing_product on channel_pricing (channel_product_id);


-- 오디언스 규모 (채널 단위, product_id 보통 null)
create table channel_audience_metrics (
                                          id                 uuid         primary key default gen_random_uuid(),
                                          channel_id         uuid         references channels(id),
                                          channel_product_id uuid         references channel_products(id),
                                          metric_name        varchar(255) not null,
                                          value_numeric      numeric,
                                          value_text         varchar(255),
                                          unit               varchar(50),
                                          period             varchar(50),
                                          source_document_id uuid         references source_documents(id),
                                          verified           boolean      not null default false
);
create index idx_audience_channel on channel_audience_metrics (channel_id);


-- 매체 사례 / 벤치마크
create table channel_references (
                                    id                 uuid         primary key default gen_random_uuid(),
                                    channel_id         uuid         references channels(id),
                                    channel_product_id uuid         references channel_products(id),
                                    result_text        text,
                                    is_benchmark       boolean      not null default false,
                                    source_document_id uuid         references source_documents(id),
                                    verified           boolean      not null default false
);
create index idx_chref_channel on channel_references (channel_id);