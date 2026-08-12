update onboarding_responses set budget_min = 0 where budget_min is null;
update onboarding_responses set budget_max = 0 where budget_max is null;
update onboarding_responses set period = 'M1' where period is null;

alter table onboarding_responses
    alter column budget_min set not null,
    alter column budget_max set not null,
    alter column period set not null;
