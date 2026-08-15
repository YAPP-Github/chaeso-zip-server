alter table budget_simulations
    add column if not exists service_name varchar(255);

alter table channel_recommendations
    add column if not exists service_name varchar(255);
