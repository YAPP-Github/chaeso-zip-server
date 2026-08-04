create table budget_simulations (
    id                    uuid        primary key default gen_random_uuid(),
    user_id               uuid        not null references users (id),
    total_budget_won      bigint      not null,
    budget_basis          varchar(20) not null,
    period                varchar(20) not null,
    total_est_impressions bigint      not null,
    total_est_clicks      bigint      not null,
    created_at            timestamp   not null default now()
);

create index idx_budget_simulation_user_created
    on budget_simulations (user_id, created_at desc);

create table budget_simulation_items (
    id                   uuid         primary key default gen_random_uuid(),
    budget_simulation_id uuid         not null references budget_simulations (id) on delete cascade,
    channel_id           uuid         not null references channels (id),
    channel_product_id   uuid         references channel_products (id),
    sort_order           integer      not null,   -- 요청받은 매체 순서. 불러오기에서 스냅샷 순서를 그대로 재현한다
    allocated_budget_won bigint       not null,
    allocation_pct       numeric,
    est_impressions_min  bigint,
    est_impressions_max  bigint,
    est_clicks_min       bigint,
    est_clicks_max       bigint,
    cpc_won              numeric,
    cpm_won              numeric,
    is_executable        boolean      not null,
    shortfall_won        bigint,
    basis_note           varchar(500),
    created_at           timestamp    not null default now()
);

create index idx_budget_simulation_item_simulation
    on budget_simulation_items (budget_simulation_id, sort_order);
