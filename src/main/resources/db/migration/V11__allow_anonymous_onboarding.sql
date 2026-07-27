alter table onboarding_responses
    alter column user_id drop not null;

alter table ad_performances
    alter column user_id drop not null;
