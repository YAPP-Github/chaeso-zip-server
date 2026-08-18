create table channel_recommendation_results (
    id            uuid         primary key default gen_random_uuid(),
    user_id       uuid         not null references users (id),
    onboarding_id uuid         not null references onboarding_responses (id),
    service_name  varchar(255),
    created_at    timestamp    not null default now(),

    constraint uq_channel_recommendation_result_onboarding unique (onboarding_id)
);

create index idx_channel_recommendation_result_user_created
    on channel_recommendation_results (user_id, created_at desc);

insert into channel_recommendation_results (user_id, onboarding_id, service_name, created_at)
select distinct on (onboarding_id) user_id, onboarding_id, service_name, created_at
  from channel_recommendations
 order by onboarding_id, created_at;

alter table channel_recommendations
    add column result_id uuid references channel_recommendation_results (id) on delete cascade;

update channel_recommendations c
   set result_id = r.id
  from channel_recommendation_results r
 where r.onboarding_id = c.onboarding_id;

alter table channel_recommendations
    alter column result_id set not null;

create index idx_channel_recommendation_result_rank
    on channel_recommendations (result_id, rank);

alter table channel_recommendations
    drop column service_name;
