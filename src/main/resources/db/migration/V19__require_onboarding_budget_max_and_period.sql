alter table onboarding_responses
    alter column budget_max set not null,
    alter column period set not null;
